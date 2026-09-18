from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

block = '''

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

# Remove misplaced copy if the previous patch inserted it near CustomerBackupScreen.
settings_section = s.find('\n@Composable\nprivate fun SettingsSection')
settings_screen = s.find('@Composable\nprivate fun SettingsScreen(')
if settings_section > 0:
    bad = s.rfind(block, settings_screen, settings_section)
    if bad >= 0:
        s = s[:bad] + s[bad + len(block):]

# Insert the controls inside SettingsScreen, immediately before that function closes.
old_tail = '''            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
'''
new_tail = '''            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
''' + block + '''}
'''
if old_tail in s and block not in s[settings_screen:s.find('\nprivate fun fmtMoney', settings_screen)]:
    s = s.replace(old_tail, new_tail, 1)

p.write_text(s)
print('anchor settings scope fixed')
