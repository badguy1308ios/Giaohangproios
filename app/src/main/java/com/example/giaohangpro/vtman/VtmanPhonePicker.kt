package com.example.giaohangpro.vtman

internal object VtmanPhonePicker {
    private const val TITLE = "Chọn số điện thoại để gọi"
    private fun normalized(value: String) = value.replace(Regex("\\s+"), " ").trim()
    fun isTitle(value: String) = normalized(value).contains(TITLE, ignoreCase = true)

    fun firstPhone(texts: List<VtmanScreenText>): String? {
        val titleNodes = texts.filter { isTitle(it.value) }
        if (titleNodes.isEmpty()) return null
        // Prefer a separate heading over a parent description spanning the entire sheet.
        val title = titleNodes.filter { normalized(it.value).equals(TITLE, true) }
            .minByOrNull { it.bottom - it.top } ?: titleNodes.minBy { it.top }
        val firstRow = texts.asSequence()
            .filter { it.top >= title.top && !isTitle(it.value) && it.bottom > it.top }
            .mapNotNull { node ->
                val number = node.value.trim().replace(Regex("[\\s().-]"), "")
                if (Regex("(?:0\\d{9}|\\+84\\d{9})").matches(number)) node to number.replaceFirst("+84", "0")
                else null
            }
            .sortedWith(compareBy<Pair<VtmanScreenText, String>> { it.first.top }.thenBy { it.first.left })
            .firstOrNull()?.second
        if (firstRow != null) return firstRow
        // Some ROMs expose the heading and both rows in one accessibility description.
        // Parse only the section after the heading, with a fixed 10-digit local number;
        // never join separate phone rows and accidentally append the next number's 0.
        return titleNodes.sortedBy { it.top }.firstNotNullOfOrNull { node ->
            val text = normalized(node.value)
            val start = text.indexOf(TITLE, ignoreCase = true) + TITLE.length
            Regex("(?<!\\d)(?:0\\d{9}|\\+84\\d{9})(?!\\d)")
                .find(text.substring(start))?.value?.replaceFirst("+84", "0")
        }
    }
}
