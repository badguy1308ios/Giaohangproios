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

if old not in s:
    raise SystemExit('visibleGroups anchor not found')
s = s.replace(old, new, 1)

p.write_text(s)
print('order detail pending groups forced to top; route order/STT source preserved')
