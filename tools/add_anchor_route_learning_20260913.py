from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# --- Route marker state: allow blank STT while preserving old marker APIs. ---
s = s.replace(
'''data class MapOrderMarker(
    val order: Order,
    val point: MapPoint,
    val number: Int,
    val hasRealCoordinate: Boolean
)''',
'''data class MapOrderMarker(
    val order: Order,
    val point: MapPoint,
    val number: Int,
    val hasRealCoordinate: Boolean,
    val showNumber: Boolean = true
)''', 1)

# --- Persistent anchor + route-created state in MainViewModel. ---
needle = '''class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE)
'''
if needle in s and 'routeNumberingEnabled' not in s:
    s = s.replace(needle, needle + '''    var routeNumberingEnabled by mutableStateOf(prefs.getBoolean("route_numbering_enabled_v1", false))
        private set
    private var anchorLat by mutableStateOf(prefs.getString("route_anchor_lat_v1", "").orEmpty())
    private var anchorLng by mutableStateOf(prefs.getString("route_anchor_lng_v1", "").orEmpty())

    fun routeAnchorPoint(): MapPoint? = pointFromStrings(anchorLat, anchorLng)

    fun setRouteAnchor(point: MapPoint) {
        anchorLat = "%.6f".format(java.util.Locale.US, point.latitude)
        anchorLng = "%.6f".format(java.util.Locale.US, point.longitude)
        prefs.edit().putString("route_anchor_lat_v1", anchorLat).putString("route_anchor_lng_v1", anchorLng).apply()
    }

    fun enableRouteNumbering() {
        routeNumberingEnabled = true
        prefs.edit().putBoolean("route_numbering_enabled_v1", true).apply()
    }

    fun learnRoutePattern(points: List<MapPoint>) {
        if (points.size < 2) return
        val obj = runCatching { JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}") }.getOrElse { JSONObject() }
        fun cell(p: MapPoint): String {
            val scale = 500.0
            return "${kotlin.math.floor(p.latitude * scale).toInt()},${kotlin.math.floor(p.longitude * scale).toInt()}"
        }
        for (i in 0 until points.lastIndex) {
            val key = "${cell(points[i])}>${cell(points[i + 1])}"
            obj.put(key, obj.optDouble(key, 0.0) + 1.0)
        }
        prefs.edit().putString("route_learning_v1", obj.toString()).apply()
    }

    fun learnedRouteWeight(from: MapPoint, to: MapPoint): Double {
        val obj = runCatching { JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val scale = 500.0
        fun cell(p: MapPoint) = "${kotlin.math.floor(p.latitude * scale).toInt()},${kotlin.math.floor(p.longitude * scale).toInt()}"
        return obj.optDouble("${cell(from)}>${cell(to)}", 0.0)
    }
''', 1)

# --- Settings > Route: add anchor picker without replacing the old screen. ---
settings_start = s.find('@Composable\nprivate fun SettingsScreen(')
settings_end = s.find('\n@Composable\nprivate fun SettingsSection', settings_start)
if settings_start >= 0 and settings_end > settings_start:
    chunk = s[settings_start:settings_end]
    if 'showRouteSettings' not in chunk:
        chunk = chunk.replace('    val context = LocalContext.current\n', '''    val context = LocalContext.current
    val routePrefs = remember { context.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE) }
    var showRouteSettings by remember { mutableStateOf(false) }
    var showAnchorPicker by remember { mutableStateOf(false) }
    var anchorLat by remember { mutableStateOf(routePrefs.getString("route_anchor_lat_v1", "").orEmpty()) }
    var anchorLng by remember { mutableStateOf(routePrefs.getString("route_anchor_lng_v1", "").orEmpty()) }
''', 1)
        chunk = chunk.replace('SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { Toast.makeText(context,"Tuyến giao hàng",Toast.LENGTH_SHORT).show() }',
                              'SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { showRouteSettings = true }', 1)
        insert = '''

    if (showRouteSettings) {
        AlertDialog(
            onDismissRequest = { showRouteSettings = false },
            title = { Text("Tuyến giao hàng") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (anchorLat.isBlank() || anchorLng.isBlank()) "Chưa có Điểm Neo" else "Điểm Neo: $anchorLat, $anchorLng", color = TextGray, fontSize = 12.sp)
                    Button(onClick = { showRouteSettings = false; showAnchorPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Place, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ĐIỂM NEO")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRouteSettings = false }) { Text("ĐÓNG") } }
        )
    }

    if (showAnchorPicker) {
        CustomerCoordinateMapPicker(
            initialPoint = pointFromStrings(anchorLat, anchorLng),
            focusUserLocation = true,
            onDismiss = { showAnchorPicker = false },
            onSavePoint = { point ->
                anchorLat = "%.6f".format(java.util.Locale.US, point.latitude)
                anchorLng = "%.6f".format(java.util.Locale.US, point.longitude)
                routePrefs.edit().putString("route_anchor_lat_v1", anchorLat).putString("route_anchor_lng_v1", anchorLng).apply()
                showAnchorPicker = false
                Toast.makeText(context, "Đã lưu Điểm Neo", Toast.LENGTH_SHORT).show()
            }
        )
    }
'''
        last = chunk.rfind('}')
        chunk = chunk[:last] + insert + chunk[last:]
        s = s[:settings_start] + chunk + s[settings_end:]

# --- Helpers for one delivery-stop coordinate and distance. ---
helper_anchor = 'private data class DeliveryGroup(val key: String, val customer: Customer?, val orders: List<Order>)\n'
if helper_anchor in s and 'private fun deliveryGroupPoint(' not in s:
    s = s.replace(helper_anchor, helper_anchor + '''
private fun deliveryGroupPoint(group: DeliveryGroup): MapPoint? {
    group.orders.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    val c = group.customer ?: return null
    pointFromStrings(c.latitude, c.longitude)?.let { return it }
    c.extraAddresses.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    return null
}

private fun deliveryGroupHasCoordinate(group: DeliveryGroup): Boolean = deliveryGroupPoint(group) != null

private fun straightDistanceMeters(a: MapPoint, b: MapPoint): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(a.latitude)
    val p2 = Math.toRadians(b.latitude)
    val dp = Math.toRadians(b.latitude - a.latitude)
    val dl = Math.toRadians(b.longitude - a.longitude)
    val h = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) + kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
}
''', 1)

# --- MapScreen passes anchor and route state to BaseMapScreen. ---
old_call = '''    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        editingStt = editing,'''
new_call = '''    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        anchorPoint = vm.routeAnchorPoint(),
        routeNumberingEnabled = vm.routeNumberingEnabled,
        editingStt = editing,'''
s = s.replace(old_call, new_call, 1)

old_sig = '''private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    editingStt: Boolean,'''
new_sig = '''private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    anchorPoint: MapPoint?,
    routeNumberingEnabled: Boolean,
    editingStt: Boolean,'''
s = s.replace(old_sig, new_sig, 1)

s = s.replace('val mappedOrders = remember(orders, customers, driverLocation) {', 'val mappedOrders = remember(orders, customers, driverLocation, anchorPoint, routeNumberingEnabled) {', 1)
s = s.replace('''            val realPoint = orderPoint ?: customerPoint
            val displayPoint = realPoint ?: driverLocation ?: DEFAULT_MAP_POINT
            MapOrderMarker(order, displayPoint, index + 1, realPoint != null)''',
'''            val realPoint = orderPoint ?: customerPoint
            val displayPoint = realPoint ?: anchorPoint ?: driverLocation ?: DEFAULT_MAP_POINT
            MapOrderMarker(order, displayPoint, index + 1, realPoint != null, routeNumberingEnabled && realPoint != null)''', 1)

# --- Marker drawing: blue when selected, blank before route creation, orange/gray otherwise. ---
s = s.replace('''private fun createNumberBubbleDrawable(
    context: android.content.Context, // Context dùng để lấy density màn hình.
    number: Int, // Số thứ tự sẽ được vẽ trong bong bóng.
    isPending: Boolean // true = đơn chưa có tọa độ thật, dùng màu xám để phân biệt với điểm giao thật.
): android.graphics.drawable.Drawable {''',
'''private fun createNumberBubbleDrawable(
    context: android.content.Context,
    number: Int,
    isPending: Boolean,
    showNumber: Boolean = true,
    selected: Boolean = false
): android.graphics.drawable.Drawable {''', 1)
s = s.replace('''    val bubbleColor = if (isPending) android.graphics.Color.rgb(117, 132, 153) else android.graphics.Color.rgb(227, 75, 10) // Xám = chờ tọa độ, cam = tọa độ thật.''',
'''    val bubbleColor = when {
        selected -> android.graphics.Color.rgb(22, 141, 226)
        isPending -> android.graphics.Color.rgb(117, 132, 153)
        else -> android.graphics.Color.rgb(227, 75, 10)
    }''', 1)
s = s.replace('canvas.drawText(number.toString(), width / 2f, textY, paint) // Vẽ số thứ tự vào giữa bong bóng.', 'if (showNumber) canvas.drawText(number.toString(), width / 2f, textY, paint)', 1)
s = s.replace('createNumberBubbleDrawable(context, markerData.number, !markerData.hasRealCoordinate)', 'createNumberBubbleDrawable(context, markerData.number, !markerData.hasRealCoordinate, markerData.showNumber, markerData.number == selectedOrderNumber)', 1)

# --- Shared circle UI for map list; no STT until route exists. ---
if '@Composable\nprivate fun RouteStateCircle(' not in s:
    nc = s.find('@Composable\nfun NumberCircle(number: Int, selected: Boolean)')
    if nc >= 0:
        s = s[:nc] + '''@Composable
private fun RouteStateCircle(number: Int?, hasCoordinate: Boolean, selected: Boolean) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(
            when {
                selected -> Color(0xFF168DE2)
                hasCoordinate -> Orange
                else -> Color(0xFF8995A5)
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        if (number != null) Text(number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

''' + s[nc:]

s = s.replace('NumberCircle(marker.number, selected = selected || editingStt)', 'RouteStateCircle(if (marker.showNumber) marker.number else null, marker.hasRealCoordinate, selected || editingStt)')

# --- Order detail: STT is blank until route creation; pending groups stay unnumbered. ---
s = s.replace('''val routeStt = if (delivered) null else activeGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)''',
'''val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else activeGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)''', 1)

# Multi-order card: routeStt null means blank gray/orange circle, not delivered check icon.
s = s.replace('''                    if (routeStt != null) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() },
                            contentAlignment = Alignment.Center
                        ) { NumberCircle(routeStt, selected = true) }
                    } else {
                        Icon(Icons.Default.CheckCircle, "Đã giao", tint = Color(0xFF2E7D32), modifier = Modifier.size(38.dp))
                    }''',
'''                    if (delivered) {
                        Icon(Icons.Default.CheckCircle, "Đã giao", tint = Color(0xFF2E7D32), modifier = Modifier.size(38.dp))
                    } else {
                        Box(Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() }, contentAlignment = Alignment.Center) {
                            RouteStateCircle(routeStt, deliveryGroupHasCoordinate(group), false)
                        }
                    }''', 1)

# --- Create route: start at anchor, nearest-neighbour, learned transition soft bonus, pending last. ---
old_route = '''        onCreateRoute = {
            val sortedGroups = activeGroups.sortedWith(compareBy<DeliveryGroup> {
                val r = groupRepresentative(it); r.latitude.toDoubleOrNull() ?: 999.0
            }.thenBy {
                val r = groupRepresentative(it); r.longitude.toDoubleOrNull() ?: 999.0
            })
            vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
            Toast.makeText(context, "Đã tạo tuyến theo ${sortedGroups.size} điểm giao", Toast.LENGTH_SHORT).show()
        },'''
new_route = '''        onCreateRoute = {
            val located = activeGroups.filter { deliveryGroupPoint(it) != null }.toMutableList()
            val pending = activeGroups.filter { deliveryGroupPoint(it) == null }
            val sortedGroups = mutableListOf<DeliveryGroup>()
            var current = vm.routeAnchorPoint() ?: located.firstOrNull()?.let(::deliveryGroupPoint) ?: DEFAULT_MAP_POINT
            while (located.isNotEmpty()) {
                val next = located.minByOrNull { g ->
                    val p = deliveryGroupPoint(g) ?: return@minByOrNull Double.MAX_VALUE
                    straightDistanceMeters(current, p) - vm.learnedRouteWeight(current, p) * 60.0
                } ?: break
                sortedGroups += next
                current = deliveryGroupPoint(next) ?: current
                located.remove(next)
            }
            sortedGroups += pending
            vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
            vm.enableRouteNumbering()
            Toast.makeText(context, "Đã tạo tuyến theo ${sortedGroups.size} điểm giao", Toast.LENGTH_SHORT).show()
        },'''
if old_route in s:
    s = s.replace(old_route, new_route, 1)

# Learn only when user finishes/saves manual STT editing.
s = s.replace('''        onSaveRoute = {
            editMarker = null
            editNumberText = ""
            editOnMap = false
            editing = false
        },''',
'''        onSaveRoute = {
            val learnedPoints = draft.mapNotNull { key -> activeGroups.firstOrNull { it.key == key }?.let(::deliveryGroupPoint) }
            vm.learnRoutePattern(learnedPoints)
            editMarker = null
            editNumberText = ""
            editOnMap = false
            editing = false
        },''', 1)

p.write_text(s)
print('anchor, blank STT, synchronized route state and route learning patch applied')
