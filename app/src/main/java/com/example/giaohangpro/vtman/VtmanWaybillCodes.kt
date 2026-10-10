package com.example.giaohangpro.vtman

/** One complete waybill, including hyphen-separated suffixes, across scan and CSV. */
internal object VtmanWaybillCodes {
    private val format = Regex("[A-Z0-9]+(?:-[A-Z0-9]+)*", RegexOption.IGNORE_CASE)
    fun isValid(value: String): Boolean = value.length in 8..24 &&
        value.any { it in '0'..'9' } && format.matches(value)

    fun fromCsv(text: String): List<String> {
        return text.lineSequence()
            .map { line ->
                val raw = line.trim().removePrefix("\uFEFF")
                val first = if (raw.startsWith("\"")) {
                    raw.drop(1).substringBefore("\"").replace("\"\"", "\"")
                } else raw.substringBefore(',').substringBefore(';')
                first.trim().uppercase()
            }
            .filter { it.isNotBlank() }
            .filterNot { it in setOf("MA_VAN_DON", "MÃ VẬN ĐƠN", "MVĐ", "MVD", "WAYBILL") }
            .filter(::isValid)
            .distinct()
            .toList()
    }

    fun visible(texts: List<VtmanScreenText>): List<String> {
        val combinedRegex = Regex(
            "(?<![A-Z0-9-])([A-Z0-9]+(?:-[A-Z0-9]+)*)\\s+TT\\s*(?:500|505|506|507|508|515)\\b",
            RegexOption.IGNORE_CASE
        )
        val statusRegex = Regex("^TT\\s*(?:500|505|506|507|508|515)$", RegexOption.IGNORE_CASE)
        val nodes = texts.sortedWith(compareBy<VtmanScreenText> { it.top }.thenBy { it.left })
        val out = linkedSetOf<String>()

        nodes.forEachIndexed { index, node ->
            combinedRegex.find(node.value)?.groupValues?.getOrNull(1)?.uppercase()?.takeIf(::isValid)?.let(out::add)
            if (statusRegex.matches(node.value.trim())) {
                val centerY = (node.top + node.bottom) / 2
                nodes.take(index).asReversed().firstOrNull { candidate ->
                    val value = candidate.value.trim()
                    val candidateCenterY = (candidate.top + candidate.bottom) / 2
                    candidate.left < node.left &&
                        kotlin.math.abs(candidateCenterY - centerY) <= 32 &&
                        isValid(value)
                }?.value?.trim()?.uppercase()?.takeIf(::isValid)?.let(out::add)
            }
        }
        return out.toList()
    }
}
