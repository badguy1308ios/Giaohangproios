from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

old = '    val visibleGroups = activeGroups.filter(::matches) + deliveredGroups.filter(::matches)\n'
new = '''    // Tab Chi tiết đơn: các điểm CHƯA có tọa độ thật luôn nằm đầu danh sách.
    // Chỉ đổi thứ tự hiển thị; activeGroups vẫn giữ thứ tự tuyến gốc để STT đã tạo không bị lệch.
    val matchedActiveGroups = activeGroups.filter(::matches)
    val pendingGroups = matchedActiveGroups.filterNot(::deliveryGroupHasCoordinate)
    val locatedGroups = matchedActiveGroups.filter(::deliveryGroupHasCoordinate)
    val visibleGroups = pendingGroups + locatedGroups + deliveredGroups.filter(::matches)
'''

if old in s:
    s = s.replace(old, new, 1)
elif new not in s:
    raise SystemExit('visibleGroups anchor not found')

# Bấm Đã giao phải xác nhận trước, tránh chạm nhầm làm mất điểm giao khỏi tuyến.
state_anchor = '    var confirmDelete by remember { mutableStateOf(false) }\n'
state_line = '    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }\n'
if state_line not in s:
    if state_anchor not in s:
        raise SystemExit('delivery confirmation state anchor not found')
    s = s.replace(state_anchor, state_anchor + state_line, 1)

old_callback = '                            onDelivered = { group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) } }\n'
new_callback = '                            onDelivered = { pendingDeliveredGroup = group }\n'
if old_callback in s:
    s = s.replace(old_callback, new_callback, 1)
elif new_callback not in s:
    raise SystemExit('onDelivered callback anchor not found')

dialog = '''    pendingDeliveredGroup?.let { group ->
        val firstCode = group.orders.firstOrNull()?.code.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeliveredGroup = null },
            title = { Text("Xác nhận đã giao?") },
            text = {
                Text(
                    if (group.orders.size > 1)
                        "Đánh dấu toàn bộ ${group.orders.size} MVĐ của điểm giao này là Đã giao?"
                    else
                        "Đánh dấu MVĐ $firstCode là Đã giao?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) }
                    pendingDeliveredGroup = null
                }) { Text("XÁC NHẬN", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeliveredGroup = null }) { Text("HỦY") }
            }
        )
    }

'''
insert_anchor = '    if (editPicker) {\n'
if dialog not in s:
    if insert_anchor not in s:
        raise SystemExit('editPicker dialog anchor not found')
    s = s.replace(insert_anchor, dialog + insert_anchor, 1)

p.write_text(s)
print('order detail pending groups stay on top; delivered action now requires confirmation')
