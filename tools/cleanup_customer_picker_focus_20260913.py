from pathlib import Path
import re

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()


def find_function_spans(text: str, name: str):
    spans = []
    pattern = re.compile(r'@Composable\s+private fun\s+' + re.escape(name) + r'\s*\(')
    for m in list(pattern.finditer(text)):
        brace = text.find('{', m.end())
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
                    spans.append((m.start(), i + 1))
                    break
            i += 1
    return spans


customer_picker = r'''@Composable
private fun CustomerCoordinateMapPicker(
    initialPoint: MapPoint?,
    focusUserLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val driverLocation by rememberDriverLocation()

    // Hai trường hợp độc lập, không được đè lên nhau:
    // 1) Khách đã có tọa độ thật -> ưu tiên focus đúng tọa độ khách đúng 1 lần khi mở.
    // 2) Khách chưa có tọa độ -> chờ GPS người dùng và focus đúng 1 lần đầu tiên.
    var selected by remember(initialPoint) { mutableStateOf(initialPoint ?: DEFAULT_MAP_POINT) }
    var gpsAppliedOnce by remember(initialPoint) { mutableStateOf(initialPoint != null) }

    LaunchedEffect(initialPoint, driverLocation, focusUserLocation) {
        if (initialPoint == null && focusUserLocation && !gpsAppliedOnce) {
            driverLocation?.let {
                selected = it
                gpsAppliedOnce = true
            }
        }
    }

    val initialFocusPoint = initialPoint ?: if (focusUserLocation) driverLocation else null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(Modifier.fillMaxSize()) {
                CustomerPageHeader(title = "CHỌN TỌA ĐỘ TRÊN BẢN ĐỒ", onBack = onDismiss)
                CoordinatePickerMap(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    initialFocusPoint = initialFocusPoint,
                    driverLocation = driverLocation,
                    selectedPoint = selected,
                    onPointSelected = { selected = it }
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%.6f, %.6f".format(java.util.Locale.US, selected.latitude, selected.longitude),
                        modifier = Modifier.weight(1f),
                        color = TextGray,
                        fontSize = 11.sp
                    )
                    driverLocation?.let { mine ->
                        TextButton(onClick = { selected = mine }) { Text("Vị trí tôi", fontSize = 11.sp) }
                    }
                    Button(onClick = { onSavePoint(selected) }, modifier = Modifier.height(40.dp)) {
                        Text("LƯU TỌA ĐỘ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}'''

coordinate_map = r'''@Composable
private fun CoordinatePickerMap(
    modifier: Modifier,
    initialFocusPoint: MapPoint?,
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
    var initialCameraFocused by remember { mutableStateOf(false) }

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
                        val first = initialFocusPoint ?: selectedPoint
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(first.latitude, first.longitude))
                            .zoom(16.0)
                            .build()
                        if (initialFocusPoint != null) initialCameraFocused = true
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

    // Camera chỉ được tự focus một lần cho mỗi lần mở picker.
    // Khi khách có tọa độ thật, initialFocusPoint chính là tọa độ khách.
    // Khi khách chưa có tọa độ, initialFocusPoint chỉ xuất hiện khi GPS đầu tiên có dữ liệu.
    LaunchedEffect(readyMap, initialFocusPoint) {
        val map = readyMap ?: return@LaunchedEffect
        val point = initialFocusPoint ?: return@LaunchedEffect
        if (!initialCameraFocused) {
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(point.latitude, point.longitude))
                        .zoom(16.0)
                        .build()
                )
            )
            initialCameraFocused = true
        }
    }

    // GPS vẫn được dùng để vẽ biểu tượng vị trí người dùng, nhưng tuyệt đối không kéo camera theo nữa.
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
}'''


def replace_all_named(text: str, name: str, canonical: str):
    spans = find_function_spans(text, name)
    if not spans:
        raise SystemExit(f'Cannot find {name}')
    # Replace first occurrence, delete any duplicate implementations after it.
    first_start, first_end = spans[0]
    text = text[:first_start] + canonical + text[first_end:]
    # Re-scan because indexes changed.
    spans = find_function_spans(text, name)
    for start, end in reversed(spans[1:]):
        text = text[:start] + text[end:]
    return text

s = replace_all_named(s, 'CustomerCoordinateMapPicker', customer_picker)
s = replace_all_named(s, 'CoordinatePickerMap', coordinate_map)

# Normalize all customer-form picker calls to the single canonical implementation.
s = re.sub(
    r'CustomerCoordinateMapPicker\(\s*initialPoint\s*=\s*([^,\n]+),\s*focusUserLocation\s*=\s*true,',
    r'CustomerCoordinateMapPicker(initialPoint = \1, focusUserLocation = true,',
    s
)

p.write_text(s)
print('customer coordinate picker cleaned: one canonical picker + one canonical map; camera auto-focus is one-shot only')
