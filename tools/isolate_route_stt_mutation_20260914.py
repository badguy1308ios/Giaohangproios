from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

marker = 'private val routeSttState = mutableStateMapOf<String, Int>()'
if marker not in s:
    anchor = '''    private var routeAnchorLat by mutableStateOf(prefs.getString("route_anchor_lat_v1", "").orEmpty())
    private var routeAnchorLng by mutableStateOf(prefs.getString("route_anchor_lng_v1", "").orEmpty())
'''
    insert = anchor + '''
    // STT tuyến được lưu tách khỏi vị trí phần tử trong orderState.
    // Giao hàng / xóa đơn tuyệt đối không dồn STT; chỉ các thao tác sửa/tạo tuyến mới ghi lại bảng này.
    private val routeSttState = mutableStateMapOf<String, Int>().apply {
        val obj = runCatching {
            JSONObject(prefs.getString("route_stt_map_v1", "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val value = obj.optInt(code, 0)
            if (value > 0) put(code, value)
        }
    }

    private fun persistRouteStt() {
        val obj = JSONObject()
        routeSttState.forEach { (code, stt) -> obj.put(code, stt) }
        prefs.edit().putString("route_stt_map_v1", obj.toString()).apply()
    }

    fun routeSttFor(code: String): Int? = routeSttState[code]

    fun routeSttForGroup(codes: List<String>): Int? =
        codes.mapNotNull { routeSttState[it] }.minOrNull()

    fun ensureRouteSttGroups(groups: List<List<String>>) {
        if (!routeNumberingEnabled) return
        var changed = false
        var next = (routeSttState.values.maxOrNull() ?: 0) + 1
        groups.forEach { codes ->
            val existing = codes.mapNotNull { routeSttState[it] }.minOrNull()
            val stt = existing ?: next++
            codes.forEach { code ->
                if (routeSttState[code] == null) {
                    routeSttState[code] = stt
                    changed = true
                }
            }
        }
        if (changed) persistRouteStt()
    }

    fun replaceRouteStt(groups: List<List<String>>) {
        routeSttState.clear()
        groups.forEachIndexed { index, codes ->
            codes.forEach { code -> routeSttState[code] = index + 1 }
        }
        persistRouteStt()
    }
'''
    if anchor not in s:
        raise SystemExit('route anchor fields not found')
    s = s.replace(anchor, insert, 1)

# Seed legacy installs once after orders/customers have loaded. This preserves current numbers at migration time.
old = '''        if (!loaded) {
            orderState.addAll(sampleOrders)
            customerState.addAll(sampleCustomers)
            savePersistentData()
        }
    }
'''
new = '''        if (!loaded) {
            orderState.addAll(sampleOrders)
            customerState.addAll(sampleCustomers)
            savePersistentData()
        }
        if (routeNumberingEnabled) {
            val groups = buildDeliveryGroups(
                orderState.filterNot { isTerminalOrderStatus(it.status) },
                customerState
            )
            ensureRouteSttGroups(groups.map { g -> g.orders.map(Order::code) })
        }
    }
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'ensureRouteSttGroups(groups.map { g -> g.orders.map(Order::code) })' not in s:
    raise SystemExit('init migration anchor not found')

# Clearing route numbering must also clear the persistent STT table.
old = '''    fun clearRouteNumbering() {
        routeNumberingEnabled = false
        prefs.edit().putBoolean("route_numbering_enabled_v1", false).apply()
    }
'''
new = '''    fun clearRouteNumbering() {
        routeNumberingEnabled = false
        routeSttState.clear()
        prefs.edit()
            .putBoolean("route_numbering_enabled_v1", false)
            .remove("route_stt_map_v1")
            .apply()
    }
'''
if old in s:
    s = s.replace(old, new, 1)
elif '.remove("route_stt_map_v1")' not in s:
    raise SystemExit('clearRouteNumbering anchor not found')

# Map uses persisted STT slots, not the current filtered-list index.
old = '''    val stableRouteNumbers = numberedRouteGroups.flatMapIndexed { index, group ->
        group.orders.map { it.code to (index + 1) }
    }.toMap()
'''
new = '''    LaunchedEffect(numberedRouteGroups.map { g -> g.orders.map(Order::code) }, vm.routeNumberingEnabled) {
        if (vm.routeNumberingEnabled) {
            vm.ensureRouteSttGroups(numberedRouteGroups.map { g -> g.orders.map(Order::code) })
        }
    }
    val stableRouteNumbers = numberedRouteGroups.flatMapIndexed { index, group ->
        val stableStt = vm.routeSttForGroup(group.orders.map(Order::code)) ?: (index + 1)
        group.orders.map { it.code to stableStt }
    }.toMap()
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'val stableStt = vm.routeSttForGroup' not in s:
    raise SystemExit('stableRouteNumbers anchor not found')

# Direct STT edit is allowed to compact/reinsert, because user is explicitly editing the route.
old = '''            val byKey = activeGroups.associateBy { it.key }
            vm.reorderOrders(next.mapNotNull(byKey::get).flatMap { it.orders.map(Order::code) })
'''
new = '''            val byKey = activeGroups.associateBy { it.key }
            val reorderedGroups = next.mapNotNull(byKey::get)
            vm.reorderOrders(reorderedGroups.flatMap { it.orders.map(Order::code) })
            vm.replaceRouteStt(reorderedGroups.map { it.orders.map(Order::code) })
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'vm.replaceRouteStt(reorderedGroups.map' not in s:
    raise SystemExit('direct STT edit anchor not found')

# Creating a new route intentionally creates a fresh compact STT sequence.
old = '''            sortedGroups += pending
            vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
            vm.enableRouteNumbering()
'''
new = '''            sortedGroups += pending
            vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
            vm.replaceRouteStt(sortedGroups.map { it.orders.map(Order::code) })
            vm.enableRouteNumbering()
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'vm.replaceRouteStt(sortedGroups.map' not in s:
    raise SystemExit('create route anchor not found')

# Importing STT is also an explicit route edit, so it may renumber.
old = '''            val ordered = codes.mapNotNull { byRep[it] }.flatMap { it.orders.map(Order::code) }
            val remaining = activeGroups.filterNot { it.orders.first().code in codes }.flatMap { it.orders.map(Order::code) }
            vm.reorderOrders(ordered + remaining)
'''
new = '''            val orderedGroups = codes.mapNotNull { byRep[it] }
            val remainingGroups = activeGroups.filterNot { it.orders.first().code in codes }
            val finalGroups = orderedGroups + remainingGroups
            vm.reorderOrders(finalGroups.flatMap { it.orders.map(Order::code) })
            vm.replaceRouteStt(finalGroups.map { it.orders.map(Order::code) })
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'vm.replaceRouteStt(finalGroups.map' not in s:
    raise SystemExit('import STT anchor not found')

# Save-confirm path (legacy UI) is an explicit route edit too.
old = '''            val groupByKey=activeGroups.associateBy{it.key}
            vm.reorderOrders(draft.mapNotNull(groupByKey::get).flatMap{it.orders.map(Order::code)})
            confirmSave=false; editing=false
'''
new = '''            val groupByKey=activeGroups.associateBy{it.key}
            val savedGroups = draft.mapNotNull(groupByKey::get)
            vm.reorderOrders(savedGroups.flatMap{it.orders.map(Order::code)})
            vm.replaceRouteStt(savedGroups.map { it.orders.map(Order::code) })
            confirmSave=false; editing=false
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'vm.replaceRouteStt(savedGroups.map' not in s:
    raise SystemExit('save-confirm route anchor not found')

# Detail tab reads the same persistent STT. Delivery/delete no longer changes later STT.
old = '''                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else numberedRouteGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)
'''
new = '''                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) {
                            null
                        } else {
                            vm.routeSttForGroup(group.orders.map(Order::code))
                                ?: numberedRouteGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)
                        }
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'vm.routeSttForGroup(group.orders.map(Order::code))' not in s:
    raise SystemExit('detail route STT anchor not found')

# Remove accidental duplicate focus effect left by older patch runs.
dup = '''    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }

    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }
'''
single = '''    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }
'''
if dup in s:
    s = s.replace(dup, single, 1)

p.write_text(s)
print('isolated route STT mutation: delivery/delete preserve gaps; only explicit route edit/create renumbers')
