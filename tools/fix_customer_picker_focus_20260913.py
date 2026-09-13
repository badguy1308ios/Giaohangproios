from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
old='''    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }
    val start = if (focusUserLocation) (driverLocation ?: initialPoint ?: DEFAULT_MAP_POINT) else (initialPoint ?: DEFAULT_MAP_POINT)

    // GPS thường trả về sau khi dialog đã mở. Khi có vị trí thật, chọn và focus ngay vào người dùng.
    LaunchedEffect(driverLocation, focusUserLocation) {
        if (focusUserLocation) driverLocation?.let { selected = it }
    }'''
new='''    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }
    val start = initialPoint ?: (if (focusUserLocation) driverLocation else null) ?: DEFAULT_MAP_POINT
    var didInitialGpsFocus by remember { mutableStateOf(initialPoint != null || !focusUserLocation) }

    // Chỉ focus GPS đúng một lần khi khách chưa có tọa độ.
    LaunchedEffect(driverLocation, focusUserLocation, initialPoint) {
        if (!didInitialGpsFocus && initialPoint == null && focusUserLocation && driverLocation != null) {
            selected = driverLocation
            didInitialGpsFocus = true
        }
    }'''
if old not in s:
    raise SystemExit('picker GPS block not found')
s=s.replace(old,new,1)
p.write_text(s)
print('customer picker focus fixed')
