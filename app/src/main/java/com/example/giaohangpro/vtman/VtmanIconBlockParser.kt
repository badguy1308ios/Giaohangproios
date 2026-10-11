package com.example.giaohangpro.vtman

/** Image elements and their screen bounds; no icon-shape classification. */
data class VtmanElement(
    val parent: Int, val left: Int, val top: Int, val right: Int, val bottom: Int,
    val text: String = "", val image: Boolean = false
)

object VtmanIconBlockParser {
    private val statusPattern = Regex("\\bTT\\s*(500|505|506|507|508|515)\\b", RegexOption.IGNORE_CASE)

    fun parse(nodes: List<VtmanElement>, waybill: String): VtmanOrderRecord? {
        val header = nodes.filter { VtmanFixedBlockParser.containsExpectedWaybill(it.text, waybill) }
            .minByOrNull { it.bottom - it.top } ?: return null
        val headerNodes = nodes.filter { it.top < header.bottom && it.bottom > header.top && it.text.isNotBlank() }
        val headerTexts = headerNodes.map { it.text }
        val nextHeader = nodes.filter { it.top >= header.bottom && statusPattern.containsMatchIn(it.text) }
            .minOfOrNull { it.top } ?: Int.MAX_VALUE
        val cardMiddle = (headerNodes.minOf { it.left }.toLong() + headerNodes.maxOf { it.right }) / 2

        // Only count the left-hand images in the filtered order, from top to
        // bottom. A different width/X, a flat hierarchy or extra image elements
        // must not reject the card. Nested images at the same Y count once.
        val icons = mutableListOf<VtmanElement>()
        nodes.filter {
            it.image && it.top >= header.bottom && it.bottom <= nextHeader &&
                it.right > it.left && it.bottom > it.top &&
                (it.left.toLong() + it.right) / 2 < cardMiddle
        }.sortedWith(compareBy<VtmanElement> { it.top }.thenBy { it.left }).forEach { icon ->
            if (icons.lastOrNull()?.let { icon.top < it.bottom } != true) icons += icon
        }
        if (icons.isEmpty()) return null
        // The completion action is below the first four data icons. A shop or
        // customer named Thành công does not end the order card.
        val completionTop = nodes.filter {
            it.top >= (icons.getOrNull(3)?.bottom ?: nextHeader) && it.top < nextHeader &&
                it.text.trim().equals("Thành công", true)
        }.minOfOrNull { it.top } ?: nextHeader
        val dataIcons = icons.takeWhile { it.bottom <= completionTop }

        // The first five positions are shop, recipient, address, goods, service.
        // There is no exact icon-count gate. Icons after number five are ignored.
        val fields = dataIcons.take(5)
        val end = minOf(completionTop, dataIcons.getOrNull(5)?.top ?: completionTop)
        val textLeft = fields.minOf { it.right }
        val candidates = nodes.filter {
            !it.image && it.text.isNotBlank() && it.left >= textLeft &&
                it.top >= header.bottom && it.bottom <= end && it.bottom > it.top
        }.distinctBy { listOf(it.text, it.left, it.top, it.right, it.bottom) }
        // Avoid repeating an aggregate label as well as the lines inside it.
        val texts = candidates.filter { parent -> candidates.none { child ->
            child !== parent && child.left >= parent.left && child.right <= parent.right &&
                child.top >= parent.top && child.bottom <= parent.bottom &&
                (child.top > parent.top || child.bottom < parent.bottom)
        } }
        // A text element may contain several rendered lines. Use its top as the
        // beginning of that icon's row; continue collecting until the next row.
        val starts = fields.map { icon ->
            texts.filter { it.top < icon.bottom && it.bottom > icon.top }
                .minOfOrNull { it.top } ?: icon.top
        }
        val rows = List(fields.size) { mutableListOf<VtmanElement>() }
        texts.forEach { text ->
            val row = starts.indexOfLast { text.top >= it }.coerceAtLeast(0)
            rows[row] += text
        }
        val values = rows.map { row -> row.sortedWith(
            compareBy<VtmanElement> { it.top }.thenBy { it.left }
        ).joinToString(" ") { it.text.trim() } }
        return VtmanOrderRecord(
            waybill = waybill,
            shop = values.getOrNull(0).orEmpty(),
            customer = values.getOrNull(1).orEmpty(),
            address = values.getOrNull(2).orEmpty(),
            goods = values.getOrNull(3).orEmpty(),
            service = values.getOrNull(4).orEmpty(),
            status = headerTexts.firstNotNullOfOrNull { statusPattern.find(it)?.value }
                ?.replace(" ", "").orEmpty(),
            cod = VtmanFixedBlockParser.findCod(headerTexts, waybill)
        )
    }
}
