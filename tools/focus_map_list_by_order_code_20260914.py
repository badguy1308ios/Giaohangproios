from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# When an order is focused from the Order Detail STT circle, pass the selected
# order code into the map bottom list so the list scrolls to the exact order.
old = '''                MapOrderBottomSheet(
                    orders = mappedOrders,
                    selectedNumber = selectedMarker?.number,
                    editingStt = editingStt,'''
new = '''                MapOrderBottomSheet(
                    orders = mappedOrders,
                    selectedNumber = selectedMarker?.number,
                    selectedOrderCode = selectedOrderCode,
                    editingStt = editingStt,'''
if old in s:
    s = s.replace(old, new, 1)
elif 'selectedOrderCode = selectedOrderCode' not in s:
    raise SystemExit('MapOrderBottomSheet call anchor not found')

old = '''private fun BoxScope.MapOrderBottomSheet(
    orders: List<MapOrderMarker>,
    selectedNumber: Int?,
    editingStt: Boolean,'''
new = '''private fun BoxScope.MapOrderBottomSheet(
    orders: List<MapOrderMarker>,
    selectedNumber: Int?,
    selectedOrderCode: String?,
    editingStt: Boolean,'''
if old in s:
    s = s.replace(old, new, 1)
elif 'selectedOrderCode: String?' not in s:
    raise SystemExit('MapOrderBottomSheet signature anchor not found')

old = '''    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedNumber, orders) {
        val n = selectedNumber ?: return@LaunchedEffect
        val i = orders.indexOfFirst { it.number == n }
        if (i >= 0) listState.animateScrollToItem(i)
    }'''
new = '''    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedOrderCode, selectedNumber, orders) {
        val i = selectedOrderCode
            ?.let { code -> orders.indexOfFirst { it.order.code == code } }
            ?.takeIf { it >= 0 }
            ?: selectedNumber?.let { n -> orders.indexOfFirst { it.number == n } }?.takeIf { it >= 0 }
            ?: -1
        if (i >= 0) listState.animateScrollToItem(i)
    }'''
if old in s:
    s = s.replace(old, new, 1)
elif 'LaunchedEffect(selectedOrderCode, selectedNumber, orders)' not in s:
    raise SystemExit('MapOrderBottomSheet scroll effect anchor not found')

# An external focus (for example tapping the STT circle in Order Detail) must
# keep the map's order list visible, select the exact marker, then let the list
# effect above scroll to the same order code.
old = '''    LaunchedEffect(focusOrderCode, mappedOrders) {
        val code = focusOrderCode ?: return@LaunchedEffect
        if (mappedOrders.any { it.order.code == code }) selectedOrderCode = code
        onFocusConsumed()
    }'''
new = '''    LaunchedEffect(focusOrderCode, mappedOrders) {
        val code = focusOrderCode ?: return@LaunchedEffect
        if (mappedOrders.any { it.order.code == code }) {
            mapExpanded = false
            selectedOrderCode = code
        }
        onFocusConsumed()
    }'''
if old in s:
    s = s.replace(old, new, 1)
elif 'mapExpanded = false\n            selectedOrderCode = code' not in s:
    raise SystemExit('external map focus effect anchor not found')

p.write_text(s)
print('STT click now keeps map list visible and focuses exact order on map and list')
