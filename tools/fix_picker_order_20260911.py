from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text(encoding='utf-8')

old = '''    val driverLocation by rememberDriverLocation()\n    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }\n    val start = if (focusUserLocation) (driverLocation ?: initialPoint ?: DEFAULT_MAP_POINT) else (initialPoint ?: DEFAULT_MAP_POINT)\n    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {\n        Surface(\n            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),\n            shape = RoundedCornerShape(14.dp), color = Background\n        ) {'''
new = '''    val driverLocation by rememberDriverLocation()\n    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }\n    val start = if (focusUserLocation) (driverLocation ?: initialPoint ?: DEFAULT_MAP_POINT) else (initialPoint ?: DEFAULT_MAP_POINT)\n\n    // GPS thường trả về sau khi dialog đã mở. Khi có vị trí thật, chọn và focus ngay vào người dùng.\n    LaunchedEffect(driverLocation, focusUserLocation) {\n        if (focusUserLocation) driverLocation?.let { selected = it }\n    }\n\n    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {\n        Surface(\n            modifier = Modifier\n                .fillMaxSize()\n                .statusBarsPadding()\n                .navigationBarsPadding()\n                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 28.dp),\n            shape = RoundedCornerShape(14.dp), color = Background\n        ) {'''
if old not in s:
    raise SystemExit('picker block not found')
s = s.replace(old, new, 1)

old2 = '''    LaunchedEffect(readyMap, selectedPoint, driverLocation) {\n        val map = readyMap ?: return@LaunchedEffect\n        map.clear()'''
new2 = '''    LaunchedEffect(readyMap, selectedPoint, driverLocation) {\n        val map = readyMap ?: return@LaunchedEffect\n        map.clear()'''
# keep marker refresh block; add camera focus effect separately before it
anchor = old2
insert = '''    // Mỗi khi GPS người dùng xuất hiện/cập nhật, camera focus ngay vào vị trí đó.\n    LaunchedEffect(readyMap, driverLocation) {\n        val map = readyMap ?: return@LaunchedEffect\n        driverLocation?.let { point ->\n            map.animateCamera(\n                CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0)\n            )\n        }\n    }\n\n'''
if anchor not in s:
    raise SystemExit('coordinate map effect anchor not found')
s = s.replace(anchor, insert + anchor, 1)

old3 = '''                    order.code,\n                    color = Navy,\n                    fontSize = 17.sp,\n                    fontWeight = FontWeight.ExtraBold'''
new3 = '''                    order.code,\n                    color = Navy,\n                    fontSize = 15.sp,\n                    fontWeight = FontWeight.ExtraBold'''
if old3 not in s:
    raise SystemExit('order code text block not found')
s = s.replace(old3, new3, 1)

p.write_text(s, encoding='utf-8')
print('Applied picker safe-area/focus and order-code font changes')
