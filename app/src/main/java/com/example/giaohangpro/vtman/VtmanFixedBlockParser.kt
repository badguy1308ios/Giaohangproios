package com.example.giaohangpro.vtman

data class VtmanScreenText(
    val value: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isButton: Boolean = false
)

object VtmanFixedBlockParser {
    private val statusRegex = Regex("\\bTT\\s*(500|505|506|507|508|515)\\b", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*[đd]", RegexOption.IGNORE_CASE)
    private val standaloneMoneyRegex = Regex("^\\s*(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*[đd]\\s*$", RegexOption.IGNORE_CASE)
    private val serviceCodeRegex = Regex("^(COD|PXD|XMG|SMS|PHT|TM|HDV|GBH|GGC|GGDH|GG1P|PTTX|GBP)$", RegexOption.IGNORE_CASE)
    private val genericServiceCodeRegex = Regex("^[A-Z][A-Z0-9]{1,9}$")
    private val waybillLikeRegex = Regex("(?<![A-Z0-9-])([A-Z0-9]+(?:-[A-Z0-9]+)*)(?![A-Z0-9-])", RegexOption.IGNORE_CASE)
    // Match address words, not arbitrary substrings in a shop/customer name.
    // Abbreviations need a separator or a place name: "H.Long Thành" is a hint,
    // while the shop "H.1998 Áo Thun" is not.
    private val addressHintRegex = Regex(
        "(?<![\\p{L}\\p{N}])(?:đường|phường|xã|huyện|quận|khu|ấp|tđc)(?![\\p{L}\\p{N}])|(?<![\\p{L}\\p{N}])(?:tp|t|h|đ|x)\\.(?=[\\p{L}]|[\\s\\u00A0]+\\S)|@",
        RegexOption.IGNORE_CASE
    )
    private val screenNoise = listOf("gạch phát offline", "danh sách phát", "phát thành công", "đồng bộ đơn hàng", "đồng bộ gần nhất")

    /**
     * Đọc theo cấu trúc 5 hàng hiển thị ngay dưới đúng MVĐ:
     * shop, người nhận, địa chỉ, hàng hóa và dịch vụ tùy chọn.
     * Không suy đoán loại dữ liệu bằng các chữ Tp., H., Khu... trong nội dung.
     */
    fun parsePositioned(texts: List<VtmanScreenText>, expectedWaybill: String): VtmanOrderRecord? {
        val nodes = texts
            .map { it.copy(value = it.value.trim()) }
            .filter { it.value.isNotBlank() && it.right > it.left && it.bottom > it.top }
            .distinctBy { listOf(it.value, it.left, it.top, it.right, it.bottom) }

        val header = nodes
            .filter { containsExpectedWaybill(it.value, expectedWaybill) }
            .minWithOrNull(
                compareBy<VtmanScreenText> {
                    when {
                        statusRegex.containsMatchIn(it.value) -> 0
                        it.value.length <= expectedWaybill.length + 4 -> 1
                        else -> 2
                    }
                }.thenBy { it.top }
            ) ?: return null

        // Search đôi khi còn để lộ 2 card cùng lúc. Nếu chỉ cắt theo nút
        // "Thành công", dữ liệu card kế tiếp có thể lọt vào card hiện tại.
        // Dùng hàng TT của đơn kế tiếp làm biên cứng thứ hai.
        val nextOrderTop = nodes.asSequence()
            .filter {
                it.top > header.bottom + 24 &&
                    statusRegex.containsMatchIn(it.value)
            }
            .map { statusNode ->
                val statusCenter = (statusNode.top + statusNode.bottom) / 2
                nodes.asSequence()
                    .filter { candidate ->
                        val center = (candidate.top + candidate.bottom) / 2
                        kotlin.math.abs(center - statusCenter) <= 36 &&
                            waybillLikeRegex.findAll(candidate.value).any { match ->
                                VtmanWaybillCodes.isValid(match.value)
                            }
                    }
                    .map(VtmanScreenText::top)
                    .minOrNull() ?: statusNode.top
            }
            .minOrNull() ?: Int.MAX_VALUE

        // A shop/customer may literally be named "Thành công". Only a button
        // or a label after the four content rows can terminate the card.
        val successTop = nodes.asSequence()
            .filter { it.top > header.bottom + 8 && it.top < nextOrderTop && it.value.equals("Thành công", true) }
            .filter { candidate ->
                candidate.isButton || nodes.count {
                    it.top >= header.bottom && it.top < candidate.top &&
                        !isHeaderOrNoise(it.value, expectedWaybill)
                } >= 4
            }
            .map(VtmanScreenText::top)
            .minOrNull() ?: Int.MAX_VALUE

        val blockEndTop = minOf(successTop, nextOrderTop)
        val blockNodes = nodes
            .filter { it.top >= header.top - 8 && it.top < blockEndTop }
            .sortedWith(compareBy<VtmanScreenText> { it.top }.thenBy { it.left })
        val block = blockNodes.map(VtmanScreenText::value)
        val status = block.asSequence()
            .mapNotNull { statusRegex.find(it)?.value?.replace(" ", "") }
            .firstOrNull().orEmpty()
        val cod = findCod(block, expectedWaybill)

        val headerBottom = blockNodes.asSequence()
            .filter {
                containsExpectedWaybill(it.value, expectedWaybill) ||
                    statusRegex.containsMatchIn(it.value) ||
                    isStandaloneMoneyLine(it.value) ||
                    looksLikeCodLine(it.value)
            }
            .filter { it.top <= header.bottom + 28 }
            .map(VtmanScreenText::bottom)
            .maxOrNull() ?: header.bottom

        val rowValues = blockNodes.asSequence()
            .filter { it.top >= headerBottom - 2 }
            .map(VtmanScreenText::value)
            .filterNot {
                containsExpectedWaybill(it, expectedWaybill) ||
                    statusRegex.matches(it.trim()) ||
                    isStandaloneMoneyLine(it) ||
                    looksLikeCodLine(it) ||
                    isScreenNoise(it)
            }
            .toList()

        if (rowValues.size < 4) return null
        val service = rowValues.drop(4)
            .flatMap(::extractPositionedServiceCodes)
            .distinct()
            .joinToString(" ")

        return VtmanOrderRecord(
            waybill = expectedWaybill,
            shop = rowValues[0],
            customer = rowValues[1],
            address = rowValues[2],
            goods = rowValues[3],
            service = service,
            status = status,
            cod = cod
        )
    }

    fun parse(texts: List<String>, expectedWaybill: String): VtmanOrderRecord? {
        val cleaned = texts.map(String::trim).filter(String::isNotBlank)
        val start = cleaned.indices.filter { containsExpectedWaybill(cleaned[it], expectedWaybill) }.minByOrNull { i ->
            val line = cleaned[i]
            when {
                statusRegex.containsMatchIn(line) && line.length <= expectedWaybill.length + 40 -> 0
                line.length <= expectedWaybill.length + 4 -> 1
                statusRegex.containsMatchIn(line) -> 2
                else -> 3
            }
        } ?: return null

        val following = cleaned.drop(start + 1)
        val completionOffset = following.indices.firstOrNull { index ->
            following[index].equals("Thành công", true) &&
                following.take(index).count { !isHeaderOrNoise(it, expectedWaybill) } >= 4
        }
        val completionEnd = if (completionOffset != null) start + completionOffset + 1 else cleaned.size
        // Fallback chữ chỉ dùng khi parser theo tọa độ không đủ dữ liệu. Vẫn khóa
        // khối tại TT của đơn kế tiếp sau tối thiểu 4 trường nội dung để tránh lấy chéo.
        val nextOrderStart = cleaned.indices
            .drop((start + 5).coerceAtMost(cleaned.size))
            .firstOrNull { i -> statusRegex.containsMatchIn(cleaned[i]) }
            ?: cleaned.size
        val endExclusive = minOf(completionEnd, nextOrderStart)
        val block = cleaned.subList(start, endExclusive).filterNot(::isScreenNoise)

        val status = block.asSequence()
            .mapNotNull { statusRegex.find(it)?.value?.replace(" ", "") }
            .firstOrNull().orEmpty()

        // VTMan có thể expose cả MVĐ + trạng thái + COD trên cùng một Accessibility node.
        // Vì vậy ưu tiên lấy tiền trên đúng dòng chứa MVĐ/trạng thái của đơn hiện tại.
        // Cách này vẫn tránh nhầm số nằm trong tên hàng hóa ở các dòng phía dưới.
        val cod = findCod(block, expectedWaybill)

        val content = block.drop(1)
        val service = content.asSequence()
            .flatMap { extractServiceCodes(it).asSequence() }
            .distinct()
            .joinToString(" ")
        val serviceLines = content.filter { extractServiceCodes(it).isNotEmpty() && looksLikeServiceLine(it) }.toSet()
        // Chỉ nhận một dòng là địa chỉ khi phía trước nó đã có đủ hai trường
        // nội dung: shop + tên khách. Nhờ vậy "(Tp.Hà Nội)" nằm trong tên shop
        // không còn bị nhận nhầm là địa chỉ.
        fun contentBefore(index: Int): List<String> = content.take(index).filterNot {
            statusRegex.containsMatchIn(it) ||
                isStandaloneMoneyLine(it) ||
                looksLikeCodLine(it) ||
                it in serviceLines
        }
        val addressIndex = content.indices.firstOrNull { index ->
            addressHintRegex.containsMatchIn(content[index]) && contentBefore(index).size >= 2
        } ?: -1

        val beforeAddress: List<String> =
            if (addressIndex > 0) contentBefore(addressIndex) else emptyList()

        val customer = beforeAddress.lastOrNull().orEmpty()
        val shop = if (beforeAddress.size >= 2) beforeAddress[beforeAddress.lastIndex - 1] else ""

        var addressEnd = addressIndex
        while (addressEnd >= 0 && addressEnd + 1 < content.size && content[addressEnd + 1].trimStart().startsWith("-")) {
            addressEnd++
        }
        val address = if (addressIndex >= 0) content.subList(addressIndex, addressEnd + 1).joinToString(" ") else ""
        val goodsStart = if (addressIndex >= 0) addressEnd + 1 else beforeAddress.size

        // Hàng hóa luôn là text; bắt đầu bằng số vẫn được giữ nguyên.
        val goods = content.drop(goodsStart)
            .filterNot {
                it in serviceLines ||
                    isScreenNoise(it) ||
                    statusRegex.matches(it.trim()) ||
                    isStandaloneMoneyLine(it) ||
                    looksLikeCodLine(it)
            }
            .joinToString(" ")
            .trim()

        return VtmanOrderRecord(
            expectedWaybill,
            shop = shop,
            customer = customer,
            goods = goods,
            status = status,
            cod = cod,
            address = address,
            service = service
        )
    }

    internal fun findCod(block: List<String>, expectedWaybill: String): String {
        // 1) Chính xác nhất: tiền nằm trên dòng chứa đúng MVĐ hiện tại.
        val onWaybillRow = block.asSequence()
            .filter { containsExpectedWaybill(it, expectedWaybill) }
            .mapNotNull { moneyRegex.find(it)?.value?.let(::normalizeCod) }
            .firstOrNull()
        if (!onWaybillRow.isNullOrBlank()) return onWaybillRow

        // 2) Một số layout tách MVĐ nhưng vẫn gộp TT + COD trên cùng node gần đầu block.
        val onStatusRow = block.asSequence()
            .take(4)
            .filter { statusRegex.containsMatchIn(it) }
            .mapNotNull { moneyRegex.find(it)?.value?.let(::normalizeCod) }
            .firstOrNull()
        if (!onStatusRow.isNullOrBlank()) return onStatusRow

        // 3) Dòng có nhãn COD và số tiền.
        val labeled = block.asSequence()
            .filter(::looksLikeCodLine)
            .mapNotNull { moneyRegex.find(it)?.value?.let(::normalizeCod) }
            .firstOrNull()
        if (!labeled.isNullOrBlank()) return labeled

        // 4) Fallback cũ: một dòng chỉ chứa số tiền.
        return block.asSequence()
            .map(String::trim)
            .filter(standaloneMoneyRegex::matches)
            .mapNotNull { moneyRegex.find(it)?.value?.let(::normalizeCod) }
            .firstOrNull()
            .orEmpty()
    }

    private fun isStandaloneMoneyLine(line: String): Boolean = standaloneMoneyRegex.matches(line.trim())

    private fun looksLikeCodLine(line: String): Boolean {
        val value = line.trim()
        return value.contains(Regex("\\bCOD\\b", RegexOption.IGNORE_CASE)) && moneyRegex.containsMatchIn(value)
    }

    private fun serviceTokens(line: String): List<String> = line
        .split(Regex("[^A-Za-z0-9]+"))
        .map { it.trim().uppercase() }
        .filter(String::isNotBlank)

    private fun extractServiceCodes(line: String): List<String> = serviceTokens(line)
        .filter { serviceCodeRegex.matches(it) }
        .distinct()

    // Ở parser theo 5 hàng, phần sau hàng hàng-hóa chính là hàng dịch vụ.
    // Nhận mọi mã dạng chữ/số để không bỏ sót mã mới của VTMan; dấu chấm,
    // dấu phẩy và khoảng trắng đều được xem là ký tự phân cách.
    private fun extractPositionedServiceCodes(line: String): List<String> = serviceTokens(line)
        .filter { genericServiceCodeRegex.matches(it) && !statusRegex.matches(it) }
        .distinct()

    private fun looksLikeServiceLine(line: String): Boolean {
        val tokens = serviceTokens(line)
        return tokens.isNotEmpty() && tokens.all { serviceCodeRegex.matches(it) }
    }

    private fun isHeaderOrNoise(value: String, expectedWaybill: String): Boolean =
        containsExpectedWaybill(value, expectedWaybill) || statusRegex.containsMatchIn(value) ||
            isStandaloneMoneyLine(value) || looksLikeCodLine(value) || isScreenNoise(value)

    private fun isScreenNoise(value: String): Boolean {
        val v = value.trim()
        return screenNoise.any { n -> v.equals(n, true) || v.startsWith("$n ", true) }
    }

    fun containsExpectedWaybill(value: String, expectedWaybill: String): Boolean {
        val w = expectedWaybill.trim()
        if (w.isBlank()) return false
        return Regex(
            "(?<![\\p{L}\\p{N}-])${Regex.escape(w)}(?![\\p{L}\\p{N}-])",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(value)
    }

    fun findPhone(texts: List<String>): String? =
        Regex("(?<!\\d)(?:\\+84|0)\\s*\\d(?:[ .-]*\\d){8,9}(?!\\d)")
            .find(texts.joinToString(" "))
            ?.value
            ?.replace(Regex("[^0-9+]"), "")
            ?.replaceFirst("+84", "0")

    private fun normalizeCod(value: String): String {
        val n = value.replace("d", "đ", true).replace(" ", "")
        return if (n == "8đ") "0đ" else n
    }
}
