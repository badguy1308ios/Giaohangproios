from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

start = s.find('@Composable\nprivate fun CustomerCoordinateMapPicker(')
end = s.find('\n// ================================================================\n// 6. TAB CHI TIẾT ĐƠN', start)
if start < 0 or end < 0:
    raise SystemExit('CustomerCoordinateMapPicker block not found')

block = s[start:end]
marker = '// Picker luôn hiển thị đồng thời vị trí khách đang chọn và vị trí GPS hiện tại.'

if marker not in block:
    anchor = '    var styleReady by remember { mutableStateOf(false) }\n'
    insert = '''    var styleReady by remember { mutableStateOf(false) }\n\n    // Picker luôn hiển thị đồng thời vị trí khách đang chọn và vị trí GPS hiện tại.\n    // GPS chỉ cập nhật marker người dùng; không đổi selectedPoint và không tự kéo camera sau lần focus đầu.\n    fun redrawPickerMarkers(readyMap: MapLibreMap) {\n        readyMap.clear()\n\n        driverLocation?.let { userPoint ->\n            val userIcon = org.maplibre.android.annotations.IconFactory.getInstance(context)\n                .fromBitmap(createDriverMotorbikeBitmap(context))\n            readyMap.addMarker(\n                MarkerOptions()\n                    .position(LatLng(userPoint.latitude, userPoint.longitude))\n                    .icon(userIcon)\n                    .title("🛵 Vị trí hiện tại")\n                    .snippet("GPS người dùng")\n            )\n        }\n\n        selectedPoint?.let { customerPoint ->\n            readyMap.addMarker(\n                MarkerOptions()\n                    .position(LatLng(customerPoint.latitude, customerPoint.longitude))\n                    .title("Vị trí khách hàng đang chọn")\n            )\n        }\n    }\n\n    // Mỗi khi GPS hoặc điểm khách thay đổi, chỉ vẽ lại marker. Không tác động camera.\n    LaunchedEffect(driverLocation, selectedPoint, map, styleReady) {\n        val readyMap = map ?: return@LaunchedEffect\n        if (!styleReady) return@LaunchedEffect\n        redrawPickerMarkers(readyMap)\n    }\n'''
    if anchor not in block:
        raise SystemExit('styleReady anchor not found')
    block = block.replace(anchor, insert, 1)

# Khi GPS đầu tiên xuất hiện, giữ nguyên logic focus một lần nhưng vẽ cả hai marker.
old = '''            readyMap.clear()\n            readyMap.addMarker(\n                MarkerOptions()\n                    .position(LatLng(gps.latitude, gps.longitude))\n                    .title("Vị trí đang chọn")\n            )'''
if old in block:
    block = block.replace(old, '            redrawPickerMarkers(readyMap)', 1)

# Khi style vừa sẵn sàng, vẽ đồng thời GPS và điểm khách.
old = '''                                        selectedPoint?.let { point ->\n                                            readyMap.clear()\n                                            readyMap.addMarker(\n                                                MarkerOptions()\n                                                    .position(LatLng(point.latitude, point.longitude))\n                                                    .title("Vị trí đang chọn")\n                                            )\n                                        }'''
if old in block:
    block = block.replace(old, '                                        redrawPickerMarkers(readyMap)', 1)

# Khi người dùng chạm chọn tọa độ khách, không làm mất marker GPS.
old = '''                                        readyMap.addOnMapClickListener { tapped ->\n                                            selectedPoint = MapPoint(tapped.latitude, tapped.longitude)\n                                            readyMap.clear()\n                                            readyMap.addMarker(\n                                                MarkerOptions()\n                                                    .position(tapped)\n                                                    .title("Vị trí đang chọn")\n                                            )\n                                            true\n                                        }'''
new = '''                                        readyMap.addOnMapClickListener { tapped ->\n                                            selectedPoint = MapPoint(tapped.latitude, tapped.longitude)\n                                            redrawPickerMarkers(readyMap)\n                                            true\n                                        }'''
if old in block:
    block = block.replace(old, new, 1)

s = s[:start] + block + s[end:]
p.write_text(s)
print('customer coordinate picker now shows current user GPS marker together with selected customer point')
