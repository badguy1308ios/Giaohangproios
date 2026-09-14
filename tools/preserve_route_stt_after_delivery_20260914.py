from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# This patch is intentionally idempotent. Every anchor is applied only when
# the target form is not already present.

# 1) Keep a stable route-number base that still contains locally delivered stops.
old = '''    val context = LocalContext.current
    val activeGroups = buildDeliveryGroups(vm.orders.filterNot { it.locallyDelivered || isTerminalOrderStatus(it.status) }, vm.customers)
    var editing by remember { mutableStateOf(false) }'''
new = '''    val context = LocalContext.current
    // Locally delivered stops stay in the numbering base so later STT values never collapse upward.
    // They are removed only from the visible/active map route.
    val numberedRouteGroups = buildDeliveryGroups(
        vm.orders.filterNot { isTerminalOrderStatus(it.status) },
        vm.customers
    )
    val activeGroups = numberedRouteGroups.filterNot { g -> g.orders.all { it.locallyDelivered } }
    val stableRouteNumbers = numberedRouteGroups.flatMapIndexed { index, group ->
        group.orders.map { it.code to (index + 1) }
    }.toMap()
    var editing by remember { mutableStateOf(false) }'''
if 'val stableRouteNumbers = numberedRouteGroups.flatMapIndexed' not in s:
    if old not in s:
        raise SystemExit('MapScreen activeGroups anchor not found')
    s = s.replace(old, new, 1)

old = '''        routeNumberingEnabled = vm.routeNumberingEnabled,
        editingStt = editing,'''
new = '''        routeNumberingEnabled = vm.routeNumberingEnabled,
        stableRouteNumbers = stableRouteNumbers,
        editingStt = editing,'''
if 'stableRouteNumbers = stableRouteNumbers' not in s:
    if old not in s:
        raise SystemExit('BaseMapScreen call anchor not found')
    s = s.replace(old, new, 1)

old = '''    routeNumberingEnabled: Boolean,
    editingStt: Boolean,'''
new = '''    routeNumberingEnabled: Boolean,
    stableRouteNumbers: Map<String, Int>,
    editingStt: Boolean,'''
if 'stableRouteNumbers: Map<String, Int>' not in s:
    if old not in s:
        raise SystemExit('BaseMapScreen signature anchor not found')
    s = s.replace(old, new, 1)

old = '''            MapOrderMarker(order, displayPoint, index + 1, realPoint != null, routeNumberingEnabled && realPoint != null)'''
new = '''            MapOrderMarker(
                order,
                displayPoint,
                stableRouteNumbers[order.code] ?: (index + 1),
                realPoint != null,
                routeNumberingEnabled && realPoint != null
            )'''
if 'stableRouteNumbers[order.code] ?: (index + 1)' not in s:
    if old not in s:
        raise SystemExit('MapOrderMarker numbering anchor not found')
    s = s.replace(old, new, 1)

old = '''    val mappedOrders = remember(orders, customers, driverLocation, anchorPoint, routeNumberingEnabled) {'''
new = '''    val mappedOrders = remember(orders, customers, driverLocation, anchorPoint, routeNumberingEnabled, stableRouteNumbers) {'''
if old in s:
    s = s.replace(old, new, 1)

# 2) Order detail uses the same stable numbering base; delivered blocks remain at the bottom without a circle.
order_numbering_block = '''    // Numbering base = active + locally delivered route stops, in original route order.
    // Terminal VTMan statuses never consume a route STT.
    val numberedRouteGroups = allGroups.filterNot { g ->
        g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) }
    }'''
if order_numbering_block not in s:
    anchor = '''    val activeGroups = allGroups.filterNot { g -> g.orders.all { it.locallyDelivered || isTerminalOrderStatus(it.status) } }
    val deliveredGroups = allGroups.filter { g -> g.orders.all { it.locallyDelivered } }
    val terminalGroups = allGroups.filter { g -> g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) } }'''
    if anchor not in s:
        raise SystemExit('OrderList grouping anchor not found')
    s = s.replace(anchor, anchor + '\n' + order_numbering_block, 1)

old = '''    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }
    val q = keyword.trim()'''
new = '''    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }
    var deliveryFocusCode by remember { mutableStateOf<String?>(null) }
    val q = keyword.trim()'''
if 'var deliveryFocusCode by remember' not in s:
    if old not in s:
        raise SystemExit('delivery focus state anchor not found')
    s = s.replace(old, new, 1)

old = '''    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.scrollToItem(i)
        onFocusConsumed()
    }'''
new = old + '''

    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }'''
if 'LaunchedEffect(deliveryFocusCode, visibleGroups)' not in s:
    if old not in s:
        raise SystemExit('focus effect anchor not found')
    s = s.replace(old, new, 1)

old = '''                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else activeGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)'''
new = '''                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else numberedRouteGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)'''
if old in s:
    s = s.replace(old, new, 1)

# 3) Add a callback that focuses the next STT on the always-alive map without forcing a tab switch.
old = '''    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit'''
new = '''    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onDeliveryFocusNext: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit'''
if 'onDeliveryFocusNext: (Order) -> Unit' not in s:
    if old not in s:
        raise SystemExit('OrderListScreen signature anchor not found')
    s = s.replace(old, new, 1)

old = '''                            onFocusConsumed = { returnOrderCode = null },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onCustomerClick = { order ->'''
new = '''                            onFocusConsumed = { returnOrderCode = null },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onDeliveryFocusNext = { order -> mapFocusOrderCode = order.code },
                            onCustomerClick = { order ->'''
if 'onDeliveryFocusNext = { order -> mapFocusOrderCode = order.code }' not in s:
    if old not in s:
        raise SystemExit('GiaoHangApp OrderListScreen wiring anchor not found')
    s = s.replace(old, new, 1)

old = '''                TextButton(onClick = {
                    group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) }
                    pendingDeliveredGroup = null
                }) { Text("XÁC NHẬN", fontWeight = FontWeight.Bold) }'''
new = '''                TextButton(onClick = {
                    val currentIndex = numberedRouteGroups.indexOfFirst { it.key == group.key }
                    val nextOrder = if (currentIndex >= 0) {
                        numberedRouteGroups.drop(currentIndex + 1)
                            .firstOrNull { next -> next.orders.any { !it.locallyDelivered && !isTerminalOrderStatus(it.status) } }
                            ?.orders?.firstOrNull()
                    } else null
                    group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) }
                    pendingDeliveredGroup = null
                    nextOrder?.let { next ->
                        deliveryFocusCode = next.code
                        onDeliveryFocusNext(next)
                    }
                }) { Text("XÁC NHẬN", fontWeight = FontWeight.Bold) }'''
if 'onDeliveryFocusNext(next)' not in s:
    if old not in s:
        raise SystemExit('delivered confirm anchor not found')
    s = s.replace(old, new, 1)

p.write_text(s)
print('preserve route STT gaps after delivery; remove delivered stop from map; focus next route stop')
