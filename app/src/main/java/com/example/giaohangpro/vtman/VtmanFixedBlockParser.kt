package com.example.giaohangpro.vtman

object VtmanFixedBlockParser {
    private val statusRegex = Regex("\\bTT\\s*(500|505|506|507|508|515)\\b", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*[đd]", RegexOption.IGNORE_CASE)
    private val serviceRegex = Regex("(?:^|[,\\s])(COD|PXD|SMS|PHT|TM|HDV|GBH)(?:$|[,\\s])", RegexOption.IGNORE_CASE)
    private val addressHints = listOf("@", "đường", "phường", "xã", "huyện", "quận", "tp.", "t.", "h.", "khu", "ấp")
    private val screenNoise = listOf(
        "gạch phát offline", "danh sách phát", "phát thành công", "đồng bộ đơn hàng",
        "đồng bộ gần nhất", "thành công"
    )

    fun parse(texts: List<String>, expectedWaybill: String): VtmanOrderRecord? {
        val cleaned = texts.map(String::trim).filter(String::isNotBlank)
        val start = cleaned.indices
            .filter { containsExpectedWaybill(cleaned[it], expectedWaybill) }
            .minByOrNull { i ->
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
        val rawBlock = cleaned.subList(start, endExclusive)
        val block = rawBlock.filterNot(::isScreenNoise)

        val status = block.asSequence().mapNotNull { statusRegex.find(it)?.value?.replace(" ", "") }.firstOrNull().orEmpty()
        val cod = block.asSequence().mapNotNull { moneyRegex.find(it)?.value?.let(::normalizeCod) }.firstOrNull().orEmpty()
        val content = block.drop(1)
        val service = content.lastOrNull { serviceRegex.containsMatchIn(it) }?.trim()?.trimStart(',', '-', ' ').orEmpty()
        val addressIndex = content.indexOfFirst { line -> addressHints.any { hint -> line.contains(hint, true) } }

        val beforeAddress = if (addressIndex > 0) content.take(addressIndex) else emptyList()
            .filterNot { statusRegex.containsMatchIn(it) || moneyRegex.matches(it) }
        // Trong card VTMan: sau MVĐ/TT/COD là Shop, rồi đến Tên người nhận, rồi Địa chỉ.
        // Chỉ lấy hai dòng sát trước địa chỉ, tuyệt đối không lấy chữ điều khiển ở phần đầu màn hình.
        val customer = beforeAddress.lastOrNull().orEmpty()
        val shop = if (beforeAddress.size >= 2) beforeAddress[beforeAddress.lastIndex - 1] else ""

        var addressEnd = addressIndex
        while (addressEnd >= 0 && addressEnd + 1 < content.size && content[addressEnd + 1].trimStart().startsWith("-")) addressEnd++
        val address = if (addressIndex >= 0) content.subList(addressIndex, addressEnd + 1).joinToString(" ") else ""
        val goodsStart = if (addressIndex >= 0) addressEnd + 1 else beforeAddress.size
        val goods = content.drop(goodsStart)
            .filterNot { it == service || it.trim().trimStart(',', '-', ' ') == service || isScreenNoise(it) }
            .joinToString(" ")
            .trim()

        return VtmanOrderRecord(
            waybill = expectedWaybill,
            shop = shop,
            customer = customer,
            goods = goods,
            status = status,
            cod = cod,
            address = address,
            service = service,
        )
    }

    private fun isScreenNoise(value: String): Boolean {
        val v = value.trim()
        return screenNoise.any { noise -> v.equals(noise, true) || v.startsWith("$noise ", true) }
    }

    fun containsExpectedWaybill(value: String, expectedWaybill: String): Boolean {
        val waybill = expectedWaybill.trim()
        if (waybill.isBlank()) return false
        return Regex("(?<![\\p{L}\\p{N}])${Regex.escape(waybill)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    fun findPhone(texts: List<String>): String? = Regex("(?<!\\d)(?:\\+84|0)\\s*\\d(?:[ .-]*\\d){8,9}(?!\\d)")
        .find(texts.joinToString(" "))
        ?.value
        ?.replace(Regex("[^0-9+]"), "")
        ?.replaceFirst("+84", "0")

    private fun normalizeCod(value: String): String {
        val normalized = value.replace("d", "đ", true).replace(" ", "")
        return if (normalized == "8đ") "0đ" else normalized
    }
}
