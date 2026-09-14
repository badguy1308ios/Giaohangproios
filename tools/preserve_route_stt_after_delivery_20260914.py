from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

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
if old in s:
    s = s.replace(old, new, 1)
elif 'val stableRouteNumbers = numberedRouteGroups.flatMapIndexed' not in s:
    raise SystemExit('MapScreen activeGroups anchor not found')

old = '''        routeNumberingEnabled = vm.routeNumberingEnabled,
        editingStt = editing,'''
new = '''        routeNumberingEnabled = vm.routeNumberingEnabled,
        stableRouteNumbers = stableRouteNumbers,
        editingStt = editing,'''
if old in s:
    s = s.replace(old, new, 1)
elif 'stableRouteNumbers = stableRouteNumbers' not in s:
    raise SystemExit('BaseMapScreen call anchor not found')

old = '''    routeNumberingEnabled: Boolean,
    editingStt: Boolean,'''
new = '''    routeNumberingEnabled: Boolean,
    stableRouteNumbers: Map<String, Int>,
    editingStt: Boolean,'''
if old in s:
    s = s.replace(old, new, 1)
elif 'stableRouteNumbers: Map<String, Int>' not in s:
    raise SystemExit('BaseMapScreen signature anchor not found')

old = '''            MapOrderMarker(order, displayPoint, index + 1, realPoint != null, routeNumberingEnabled && realPoint != null)'''
new = '''            MapOrderMarker(
                order,
                displayPoint,
                stableRouteNumbers[order.code] ?: (index + 1),
                realPoint != null,
                routeNumberingEnabled && realPoint != null
            )'''
if old in s:
    s = s.replace(old, new, 1)
elif 'stableRouteNumbers[order.code] ?: (index + 1)' not in s:
    raise SystemExit('MapOrderMarker numbering anchor not found')

old = '''    val mappedOrders = remember(orders, customers, driverLocation, anchorPoint, routeNumberingEnabled) {'''
new = '''    val mappedOrders = remember(orders, customers, driverLocation, anchorPoint, routeNumberingEnabled, stableRouteNumbers) {'''
if old in s:
    s = s.replace(old, new, 1)

# 2) Order detail uses the same stable numbering base; delivered blocks remain at the bottom without a circle.
old = '''    val activeGroups = allGroups.filterNot { g -> g.orders.all { it.locallyDelivered || isTerminalOrderStatus(it.status) } }
    val deliveredGroups = allGroups.filter { g -> g.orders.all { it.locallyDelivered } }
    val terminalGroups = allGroups.filter { g -> g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) } }'''
new = '''    val activeGroups = allGroups.filterNot { g -> g.orders.all { it.locallyDelivered || isTerminalOrderStatus(it.status) } }
    val deliveredGroups = allGroups.filter { g -> g.orders.all { it.locallyDelivered } }
    val terminalGroups = allGroups.filter { g -> g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) } }
    // Numbering base = active + locally delivered route stops, in original route order.
    // Terminal VTMan statuses never consume a route STT.
    val numberedRouteGroups = allGroups.filterNot { g ->
        g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) }
    }'''
if old in s:
    s = s.replace(old, new, 1)
elif 'val numberedRouteGroups = allGroups.filterNot' not in s:
    raise SystemExit('OrderList grouping anchor not found')

# Add a local delivery-focus target used to scroll the detail list after the delivered block moves down.
old = '''    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }
    val q = keyword.trim()'''
new = '''    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }
    var deliveryFocusCode by remember { mutableStateOf<String?>(null) }
    val q = keyword.trim()'''
if old in s:
    s = s.replace(old, new, 1)
elif 'var deliveryFocusCode by remember' not in s:
    raise SystemExit('delivery focus state anchor not found')

old = '''    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.scrollToItem(i)
        onFocusConsumed()
    }'''
new = '''    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.scrollToItem(i)
        onFocusConsumed()
    }

    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }'''
if old in s:
    s = s.replace(old, new, 1)
elif 'LaunchedEffect(deliveryFocusCode, visibleGroups)' not in s:
    raise SystemExit('focus effect anchor not found')

old = '''                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else activeGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)'''
new = '''                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else numberedRouteGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)'''
if old in s:
    s = s.replace(old, new, 1)
elif 'numberedRouteGroups.indexOfFirst { it.key == group.key }' not in s:
    raise SystemExit('Order detail routeStt anchor not found')

# 3) Add a callback that focuses the next STT on the always-alive map without forcing a tab switch.
old = '''    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit'''
new = '''    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onDeliveryFocusNext: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit'''
if old in s:
    s = s.replace(old, new, 1)
elif 'onDeliveryFocusNext: (Order) -> Unit' not in s:
    raise SystemExit('OrderListScreen signature anchor not found')

old = '''                            onFocusConsumed = { returnOrderCode = null },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onCustomerClick = { order ->'''
new = '''                            onFocusConsumed = { returnOrderCode = null },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onDeliveryFocusNext = { order -> mapFocusOrderCode = order.code },
                            onCustomerClick = { order ->'''
if old in s:
    s = s.replace(old, new, 1)
elif 'onDeliveryFocusNext = { order -> mapFocusOrderCode = order.code }' not in s:
    raise SystemExit('GiaoHangApp OrderListScreen wiring anchor not found')

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
if old in s:
    s = s.replace(old, new, 1)
elif 'onDeliveryFocusNext(next)' not in s:
    raise SystemExit('delivered confirm anchor not found')

p.write_text(s)
print('preserve route STT gaps after delivery; remove delivered stop from map; focus next route stop')
