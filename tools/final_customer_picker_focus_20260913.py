from pathlib import Path
import re

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()


def function_spans(text: str, name: str):
    pattern = re.compile(r'@Composable\s+private fun\s+' + re.escape(name) + r'\s*\(')
    spans = []
    for match in list(pattern.finditer(text)):
        brace = text.find('{', match.end())
        if brace < 0:
            continue
        depth = 0
        i = brace
        in_string = False
        triple = False
        escaped = False
        while i < len(text):
            if triple:
                if text.startswith('"""', i):
                    triple = False
                    i += 3
                    continue
                i += 1
                continue
            if in_string:
                c = text[i]
                if escaped:
                    escaped = False
                elif c == '\\':
                    escaped = True
                elif c == '"':
                    in_string = False
                i += 1
                continue
            if text.startswith('"""', i):
                triple = True
                i += 3
                continue
            c = text[i]
            if c == '"':
                in_string = True
            elif c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    spans.append((match.start(), i + 1))
                    break
            i += 1
    return spans


canonical = r'''@Composable
private fun CustomerCoordinateMapPicker(
    initialPoint: MapPoint?,
    focusUserLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driverLocation by rememberDriverLocation()

    // Hai quy tắc độc lập, không được chồng lên nhau:
    // - Có tọa độ thật của khách: focus tọa độ khách đúng 1 lần khi mở.
    // - Chưa có tọa độ thật: lấy GPS người dùng đúng 1 lần đầu khi GPS sẵn sàng.
    // Sau lần focus đầu tiên, các bản tin GPS tiếp theo chỉ là dữ liệu vị trí,
    // tuyệt đối không tự kéo camera và không ghi đè điểm khách đã chọn.
    var selectedPoint by remember(initialPoint) { mutableStateOf(initialPoint) }
    var initialFocusDone by remember(initialPoint, focusUserLocation) {
        mutableStateOf(!focusUserLocation && initialPoint == null)
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

    // Chỉ chạy cho trường hợp khách CHƯA có tọa độ và GPS chưa sẵn sàng lúc style vừa mở.
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

                                        when {
                                            initialPoint != null -> {
                                                // Mệnh đề 1: tọa độ thật của khách luôn thắng GPS.
                                                selectedPoint = initialPoint
                                                readyMap.cameraPosition = CameraPosition.Builder()
                                                    .target(LatLng(initialPoint.latitude, initialPoint.longitude))
                                                    .zoom(16.0)
                                                    .build()
                                                initialFocusDone = true
                                            }
                                            focusUserLocation && driverLocation != null -> {
                                                // Mệnh đề 2: nếu GPS đã có ngay lúc mở, dùng đúng lần này rồi khóa auto-focus.
                                                val gps = driverLocation!!
                                                selectedPoint = gps
                                                readyMap.cameraPosition = CameraPosition.Builder()
                                                    .target(LatLng(gps.latitude, gps.longitude))
                                                    .zoom(16.0)
                                                    .build()
                                                initialFocusDone = true
                                            }
                                            else -> {
                                                // Chờ GPS đầu tiên; không tự coi điểm mặc định là tọa độ khách.
                                                readyMap.cameraPosition = CameraPosition.Builder()
                                                    .target(LatLng(DEFAULT_MAP_POINT.latitude, DEFAULT_MAP_POINT.longitude))
                                                    .zoom(15.0)
                                                    .build()
                                            }
                                        }

                                        selectedPoint?.let { point ->
                                            readyMap.clear()
                                            readyMap.addMarker(
                                                MarkerOptions()
                                                    .position(LatLng(point.latitude, point.longitude))
                                                    .title("Vị trí đang chọn")
                                            )
                                        }

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
}'''

# Gom toàn bộ implementation CustomerCoordinateMapPicker về đúng MỘT hàm canonical.
spans = function_spans(s, 'CustomerCoordinateMapPicker')
if not spans:
    raise SystemExit('CustomerCoordinateMapPicker not found')
first_start, first_end = spans[0]
s = s[:first_start] + canonical + s[first_end:]
for start, end in reversed(function_spans(s, 'CustomerCoordinateMapPicker')[1:]):
    s = s[:start] + s[end:]

# CoordinatePickerMap là implementation cũ gây logic focus GPS chồng chéo.
# Sau khi picker canonical tự quản MapView, helper cũ không còn được dùng và được xóa sạch.
for start, end in reversed(function_spans(s, 'CoordinatePickerMap')):
    s = s[:start] + s[end:]

p.write_text(s)
print('customer coordinate picker finalized: one implementation, one-shot customer/GPS focus, no overlapping old map helper')
