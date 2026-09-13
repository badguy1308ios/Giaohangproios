from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

start = s.index('@Composable\nprivate fun CustomerCoordinateMapPicker(')
end = s.index('\n// ================================================================\n// 6. TAB CHI TIẾT ĐƠN', start)

new = r'''@Composable
private fun CustomerCoordinateMapPicker(
    initialPoint: MapPoint?,
    focusUserLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driverLocation by rememberDriverLocation()

    // Quy tắc focus duy nhất:
    // 1) Khách đã có tọa độ thật -> focus đúng tọa độ khách MỘT LẦN khi mở.
    // 2) Khách chưa có tọa độ -> khi GPS có dữ liệu, focus GPS MỘT LẦN khi mở.
    // Sau lần focus đầu tiên, GPS cập nhật tiếp cũng KHÔNG được kéo bản đồ đi nữa.
    var selectedPoint by remember(initialPoint) { mutableStateOf(initialPoint) }
    var initialFocusDone by remember(initialPoint, focusUserLocation) {
        mutableStateOf(initialPoint != null || !focusUserLocation)
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

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

    // Chỉ dành cho khách CHƯA có tọa độ thật. Khi GPS trả dữ liệu lần đầu,
    // lấy đó làm điểm đang chọn + focus đúng một lần rồi khóa auto-focus.
    LaunchedEffect(driverLocation, map, styleReady, initialPoint, focusUserLocation, initialFocusDone) {
        if (initialPoint == null && focusUserLocation && !initialFocusDone) {
            val gps = driverLocation ?: return@LaunchedEffect
            val readyMap = map ?: return@LaunchedEffect
            if (!styleReady) return@LaunchedEffect

            selectedPoint = gps
            readyMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(gps.latitude, gps.longitude),
                    16.0
                )
            )
            readyMap.clear()
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(gps.latitude, gps.longitude))
                    .title("Vị trí đang chọn")
            )
            initialFocusDone = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(Modifier.fillMaxSize()) {
                CustomerPageHeader(title = "CHỌN TỌA ĐỘ TRÊN BẢN ĐỒ", onBack = onDismiss)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            mapView.apply {
                                getMapAsync { readyMap ->
                                    readyMap.setStyle(Style.Builder().fromJson(goongStyle(context))) {
                                        map = readyMap
                                        styleReady = true

                                        // Tọa độ thật của khách luôn thắng GPS.
                                        // Chỉ set camera ở đây đúng lần khởi tạo style.
                                        val startPoint = initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT
                                        readyMap.cameraPosition = CameraPosition.Builder()
                                            .target(LatLng(startPoint.latitude, startPoint.longitude))
                                            .zoom(if (initialPoint != null) 16.0 else 15.0)
                                            .build()

                                        selectedPoint?.let { point ->
                                            readyMap.clear()
                                            readyMap.addMarker(
                                                MarkerOptions()
                                                    .position(LatLng(point.latitude, point.longitude))
                                                    .title("Vị trí đang chọn")
                                            )
                                        }

                                        // Nếu đã có tọa độ thật, lần focus đầu đã hoàn tất ngay khi mở.
                                        if (initialPoint != null) initialFocusDone = true

                                        readyMap.addOnMapClickListener { tapped ->
                                            selectedPoint = MapPoint(tapped.latitude, tapped.longitude)
                                            readyMap.clear()
                                            readyMap.addMarker(
                                                MarkerOptions()
                                                    .position(tapped)
                                                    .title("Vị trí đang chọn")
                                            )
                                            true
                                        }
                                    }
                                }
                            }
                        },
                        update = { view ->
                            view.getMapAsync { readyMap ->
                                if (readyMap.style != null) map = readyMap
                            }
                        }
                    )

                    selectedPoint?.let { point ->
                        Card(
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                        ) {
                            Text(
                                "Vĩ độ: %.6f\nKinh độ: %.6f".format(
                                    java.util.Locale.US,
                                    point.latitude,
                                    point.longitude
                                ),
                                modifier = Modifier.padding(12.dp),
                                color = Navy
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("HỦY", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { selectedPoint?.let(onSavePoint) },
                        enabled = selectedPoint != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Text("LƯU", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
'''

s = s[:start] + new + s[end:]
p.write_text(s)
print('final customer coordinate picker focus behavior applied')
