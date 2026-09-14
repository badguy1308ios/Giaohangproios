package com.example.giaohangpro.vtman

object VtmanFixedBlockParser {
    private val statusRegex = Regex("\\bTT\\s*(500|505|506|507|508|515)\\b", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*[đd]", RegexOption.IGNORE_CASE)
    private val standaloneMoneyRegex = Regex("^\\s*(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*[đd]\\s*$", RegexOption.IGNORE_CASE)
    private val serviceCodeRegex = Regex("^(COD|PXD|XMG|SMS|PHT|TM|HDV|GBH)$", RegexOption.IGNORE_CASE)
    private val addressHints = listOf("@", "đường", "phường", "xã", "huyện", "quận", "tp.", "t.", "h.", "khu", "ấp", "tđc", "đ.", "x.")
    private val screenNoise = listOf("gạch phát offline", "danh sách phát", "phát thành công", "đồng bộ đơn hàng", "đồng bộ gần nhất", "thành công")

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
        val completionOffset = following.indexOfFirst { it.equals("Thành công", true) }
        val endExclusive = if (completionOffset >= 0) start + completionOffset + 2 else cleaned.size
        val block = cleaned.subList(start, endExclusive).filterNot(::isScreenNoise)

        val status = block.asSequence()
            .mapNotNull { statusRegex.find(it)?.value?.replace(" ", "") }
            .firstOrNull().orEmpty()

        // COD chỉ được lấy từ dòng có nhãn COD hoặc một dòng tiền độc lập.
        // Không quét số tiền ở giữa chuỗi hàng hóa để tránh nhầm các mặt hàng bắt đầu bằng số.
        val cod = findCod(block)

        val content = block.drop(1)
        val service = content.asSequence()
            .flatMap { extractServiceCodes(it).asSequence() }
            .distinct()
            .joinToString(" ")
        val serviceLines = content.filter { extractServiceCodes(it).isNotEmpty() && looksLikeServiceLine(it) }.toSet()
        val addressIndex = content.indexOfFirst { line -> addressHints.any { hint -> line.contains(hint, true) } }

        val beforeAddress: List<String> = (if (addressIndex > 0) content.take(addressIndex) else emptyList())
            .filterNot {
                statusRegex.containsMatchIn(it) ||
                    isStandaloneMoneyLine(it) ||
                    looksLikeCodLine(it) ||
                    it in serviceLines
            }

        val customer = beforeAddress.lastOrNull().orEmpty()
        val shop = if (beforeAddress.size >= 2) beforeAddress[beforeAddress.lastIndex - 1] else ""

        var addressEnd = addressIndex
        while (addressEnd >= 0 && addressEnd + 1 < content.size && content[addressEnd + 1].trimStart().startsWith("-")) {
            addressEnd++
        }
        val address = if (addressIndex >= 0) content.subList(addressIndex, addressEnd + 1).joinToString(" ") else ""
        val goodsStart = if (addressIndex >= 0) addressEnd + 1 else beforeAddress.size

        // Hàng hóa luôn được coi là TEXT. Dòng bắt đầu bằng số vẫn giữ nguyên.
        // Chỉ loại các dòng chắc chắn là trạng thái/COD/dịch vụ/nhiễu giao diện.
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

    private fun findCod(block: List<String>): String {
        val labeled = block.asSequence()
            .filter(::looksLikeCodLine)
            .mapNotNull { moneyRegex.find(it)?.value?.let(::normalizeCod) }
            .firstOrNull()
        if (!labeled.isNullOrBlank()) return labeled

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

    private fun extractServiceCodes(line: String): List<String> = line
        .split(',', ' ', ';', '|')
        .map { it.trim().uppercase() }
        .filter { serviceCodeRegex.matches(it) }
        .distinct()

    private fun looksLikeServiceLine(line: String): Boolean {
        val tokens = line.split(',', ' ', ';', '|').map(String::trim).filter(String::isNotBlank)
        return tokens.isNotEmpty() && tokens.all { serviceCodeRegex.matches(it) }
    }

    private fun isScreenNoise(value: String): Boolean {
        val v = value.trim()
        return screenNoise.any { n -> v.equals(n, true) || v.startsWith("$n ", true) }
    }

    fun containsExpectedWaybill(value: String, expectedWaybill: String): Boolean {
        val w = expectedWaybill.trim()
        if (w.isBlank()) return false
        return Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(w)}(?![\\p{L}\\p{N}])",
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
