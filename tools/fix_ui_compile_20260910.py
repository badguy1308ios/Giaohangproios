from pathlib import Path
p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()
anchor = '// ================================================================\n// 10. Ô TÌM KIẾM + SỐ THỨ TỰ'
if 'fun BottomTabs(selected: Tab' not in s:
    helpers = r'''
// ================================================================
// THANH 3 TAB DƯỚI CÙNG
// ================================================================
@Composable
fun BottomTabs(selected: Tab, onSelected: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).background(Orange)) {
        BottomTabItem(selected == Tab.MAP, "Bản đồ", Icons.Default.Map) { onSelected(Tab.MAP) }
        BottomTabItem(selected == Tab.ORDERS, "Chi tiết đơn", Icons.Default.ReceiptLong) { onSelected(Tab.ORDERS) }
        BottomTabItem(selected == Tab.CUSTOMERS, "Khách hàng", Icons.Default.People) { onSelected(Tab.CUSTOMERS) }
    }
}

@Composable
fun RowScope.BottomTabItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFFF3F0EC) else Orange
    val fg = if (selected) OrangeDark else Color.White
    Column(
        Modifier.weight(1f).fillMaxHeight().background(bg).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(19.dp))
        Text(label, color = fg, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun createInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
private fun CoordinatePickerMap(
    modifier: Modifier,
    initialPoint: MapPoint,
    driverLocation: MapPoint?,
    selectedPoint: MapPoint,
    onPointSelected: (MapPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var readyMap by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromJson(goongStyle(context))) {
                        readyMap = map
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(initialPoint.latitude, initialPoint.longitude))
                            .zoom(16.0)
                            .build()
                        map.addOnMapClickListener { latLng ->
                            onPointSelected(MapPoint(latLng.latitude, latLng.longitude))
                            true
                        }
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { map ->
                if (map.style != null) readyMap = map
            }
        }
    )

    LaunchedEffect(readyMap, selectedPoint, driverLocation) {
        val map = readyMap ?: return@LaunchedEffect
        map.clear()
        val selectedBitmap = createNumberBubbleDrawable(context, 1, false)
        val selectedIcon = IconFactory.getInstance(context).fromBitmap(
            (selectedBitmap as android.graphics.drawable.BitmapDrawable).bitmap
        )
        map.addMarker(
            MarkerOptions()
                .position(LatLng(selectedPoint.latitude, selectedPoint.longitude))
                .icon(selectedIcon)
                .title("Vị trí đã chọn")
        )
        driverLocation?.let { point ->
            val driverIcon = IconFactory.getInstance(context).fromBitmap(createDriverMotorbikeBitmap(context))
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .icon(driverIcon)
                    .title("Vị trí của tôi")
            )
        }
    }
}
'''
    if anchor not in s:
        raise SystemExit('Missing section 10 anchor')
    s = s.replace(anchor, helpers + '\n' + anchor, 1)

# Requested one-size-larger STT bubble in map/list.
s = s.replace('.size(24.dp) // Bong bóng STT nhỏ gọn 24dp.', '.size(28.dp) // Bong bóng STT lớn hơn một nấc.', 1)
s = s.replace('fontSize = 12.sp, // Số STT 12sp.', 'fontSize = 13.sp, // Số STT lớn hơn một nấc.', 1)
p.write_text(s)
print('compile helpers restored')
