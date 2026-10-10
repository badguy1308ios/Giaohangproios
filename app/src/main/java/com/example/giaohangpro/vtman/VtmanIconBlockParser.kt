package com.example.giaohangpro.vtman

/** Snapshot of actual accessibility elements; image means an exposed image widget,
 * not a guessed role derived from its text or its ordinal text position. */
data class VtmanElement(
    val parent: Int, val left: Int, val top: Int, val right: Int, val bottom: Int,
    val text: String = "", val image: Boolean = false
)

object VtmanIconBlockParser {
    private val statusPattern = Regex("\\bTT\\s*(500|505|506|507|508|515)\\b", RegexOption.IGNORE_CASE)

    fun parse(nodes: List<VtmanElement>, waybill: String): VtmanOrderRecord? {
        val header = nodes.filter { VtmanFixedBlockParser.containsExpectedWaybill(it.text, waybill) }
            .minByOrNull { it.bottom - it.top } ?: return null
        val headerTexts = nodes.filter { it.top < header.bottom && it.bottom > header.top }
            .map { it.text }.filter { it.isNotBlank() }
        val status = headerTexts.firstNotNullOfOrNull { statusPattern.find(it)?.value }
            ?.replace(" ", "").orEmpty()
        val nextHeader = nodes.filter { it.top >= header.bottom && statusPattern.containsMatchIn(it.text) }
            .minOfOrNull { it.top } ?: Int.MAX_VALUE
        val images = nodes.indices.filter { i ->
            val n = nodes[i]
            n.image && n.top >= header.bottom && n.bottom <= nextHeader &&
                n.right > n.left && n.bottom > n.top
        }
        // The field icons form the left column. The call icon on the right is
        // excluded without recognizing any icon shape or looking up a name.
        val leftEdge = images.minOfOrNull { nodes[it].left } ?: return null
        val first = images.minByOrNull { nodes[it].left } ?: return null
        val tolerance = (nodes[first].right - nodes[first].left) / 2
        val icons = images.filter { kotlin.math.abs(nodes[it].left - leftEdge) <= tolerance }
            .distinctBy { listOf(nodes[it].left, nodes[it].top, nodes[it].right, nodes[it].bottom) }
            .sortedBy { nodes[it].top }
        if (icons.size !in 4..5) return null

        fun descendant(index: Int, ancestor: Int): Boolean {
            var cursor = index
            var remaining = nodes.size
            while (cursor >= 0 && remaining-- > 0) {
                if (cursor == ancestor) return true
                cursor = nodes.getOrNull(cursor)?.parent ?: return false
            }
            return false
        }
        fun rowText(iconIndex: Int): String? {
            val icon = nodes[iconIndex]
            var ancestor = icon.parent
            var remaining = nodes.size
            while (ancestor >= 0 && remaining-- > 0) {
                // Use the smallest enclosing row with one left-column icon and
                // text. This preserves wrapped lines even with a centered icon.
                if (icons.count { descendant(it, ancestor) } != 1) return null
                val candidates = nodes.indices.filter { i ->
                    val n = nodes[i]
                    descendant(i, ancestor) && !n.image && n.text.isNotBlank() &&
                        n.left >= icon.right && n.top >= header.bottom && n.bottom <= nextHeader
                }
                // A parent may repeat all child text: retain the child leaves.
                val leaves = candidates.filter { i ->
                    candidates.none { j -> j != i && descendant(j, i) }
                }.map { nodes[it] }.distinctBy { listOf(it.text, it.left, it.top, it.right, it.bottom) }
                    .sortedWith(compareBy<VtmanElement> { it.top }.thenBy { it.left })
                if (leaves.isNotEmpty()) return leaves.joinToString(" ") { it.text.trim() }
                ancestor = nodes.getOrNull(ancestor)?.parent ?: return null
            }
            return null
        }
        val rows = icons.map(::rowText)
        if (rows.take(4).any { it.isNullOrBlank() }) return null
        return VtmanOrderRecord(
            waybill = waybill, shop = rows[0].orEmpty(), customer = rows[1].orEmpty(),
            address = rows[2].orEmpty(), goods = rows[3].orEmpty(),
            service = rows.getOrNull(4).orEmpty(), status = status,
            cod = VtmanFixedBlockParser.findCod(headerTexts, waybill)
        )
    }
}
