from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text(encoding='utf-8')
changed = False

# Keep the whole dialog comfortably above 3-button/gesture navigation on devices
# whose Dialog window reports insets inconsistently.
old = '''                .fillMaxSize()\n                .statusBarsPadding()\n                .navigationBarsPadding()\n                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 28.dp),'''
new = '''                .fillMaxSize()\n                .statusBarsPadding()\n                .navigationBarsPadding()\n                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 72.dp),'''
if old in s:
    s = s.replace(old, new, 1)
    changed = True

# Put both actions in a dedicated compact footer that is always above navigation.
old = '''                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {\n                    Button(onClick = { onSavePoint(selected) }, modifier = Modifier.height(40.dp)) { Text("LƯU TỌA ĐỘ", fontSize = 12.sp) }\n                }'''
new = '''                Row(\n                    Modifier.fillMaxWidth().height(48.dp).background(Color.White).padding(horizontal = 6.dp, vertical = 4.dp),\n                    horizontalArrangement = Arrangement.spacedBy(6.dp)\n                ) {\n                    OutlinedButton(\n                        onClick = onDismiss,\n                        modifier = Modifier.weight(0.35f).fillMaxHeight(),\n                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)\n                    ) { Text("HỦY", fontSize = 11.sp, fontWeight = FontWeight.Bold) }\n                    Button(\n                        onClick = { onSavePoint(selected) },\n                        modifier = Modifier.weight(0.65f).fillMaxHeight(),\n                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)\n                    ) { Text("LƯU TỌA ĐỘ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }\n                }'''
if old in s:
    s = s.replace(old, new, 1)
    changed = True

# Older source fallback: order code 17sp -> 15sp. Safe to skip once already applied.
old = '''                    order.code,\n                    color = Navy,\n                    fontSize = 17.sp,\n                    fontWeight = FontWeight.ExtraBold'''
new = '''                    order.code,\n                    color = Navy,\n                    fontSize = 15.sp,\n                    fontWeight = FontWeight.ExtraBold'''
if old in s:
    s = s.replace(old, new, 1)
    changed = True

# GPS focus effect is added only if an older source does not already contain it.
marker_anchor = '''    LaunchedEffect(readyMap, selectedPoint, driverLocation) {\n        val map = readyMap ?: return@LaunchedEffect\n        map.clear()'''
focus_block = '''    // Mỗi khi GPS người dùng xuất hiện/cập nhật, camera focus ngay vào vị trí đó.\n    LaunchedEffect(readyMap, driverLocation) {\n        val map = readyMap ?: return@LaunchedEffect\n        driverLocation?.let { point ->\n            map.animateCamera(\n                CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0)\n            )\n        }\n    }\n\n'''
if 'Mỗi khi GPS người dùng xuất hiện/cập nhật' not in s and marker_anchor in s:
    s = s.replace(marker_anchor, focus_block + marker_anchor, 1)
    changed = True

if changed:
    p.write_text(s, encoding='utf-8')
    print('Applied latest coordinate picker fixes')
else:
    print('Latest coordinate picker fixes already applied')
