from pathlib import Path
import re

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# 1) Popup Sửa đơn: luôn lấy danh sách ứng viên trực tiếp từ keyword hiện tại,
# tránh dùng state/list cũ sau các lần thay đổi giao diện nhóm.
old = '''    if (editPicker) AlertDialog(
        onDismissRequest = { editPicker = false },
        title = { Text("Chọn đơn cần sửa") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                filteredOrders.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { editOrder = o; editPicker = false }.padding(10.dp)) {
                        Text(o.code, Modifier.weight(1f))
                        Text(o.customer, fontSize = 11.sp, color = TextGray)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { editPicker = false }) { Text("ĐÓNG") } }
    )
'''
new = '''    if (editPicker) {
        val editQuery = keyword.trim()
        val editCandidates = vm.orders.filter { o ->
            editQuery.isBlank() || o.code.contains(editQuery, true) || o.customer.contains(editQuery, true) ||
                o.phone.contains(editQuery, true) || o.address.contains(editQuery, true) ||
                o.shop.contains(editQuery, true) || o.item.contains(editQuery, true)
        }
        AlertDialog(
            onDismissRequest = { editPicker = false },
            title = { Text("Chọn đơn cần sửa") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    editCandidates.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editOrder = o; editPicker = false }.padding(vertical = 9.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(o.code, color = Navy, fontWeight = FontWeight.Bold)
                                Text("${o.customer} • ${o.phone}", fontSize = 11.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (editCandidates.isEmpty()) Text("Không tìm thấy đơn phù hợp", color = TextGray, modifier = Modifier.padding(10.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editPicker = false }) { Text("ĐÓNG") } }
        )
    }
'''
if old in s:
    s = s.replace(old, new, 1)

# 2) Khôi phục đầy đủ trường sửa đơn: Shop, Hàng hóa, Dịch vụ; giữ nguyên các trường cũ.
start_token = '    editOrder?.let { original ->\n'
end_token = '\n    if (confirmDelete) AlertDialog('
start = s.find(start_token)
end = s.find(end_token, start) if start >= 0 else -1
if start >= 0 and end > start:
    editor = '''    editOrder?.let { original ->
        var shop by remember(original.code) { mutableStateOf(original.shop) }
        var customer by remember(original.code) { mutableStateOf(original.customer) }
        var phone by remember(original.code) { mutableStateOf(original.phone) }
        var address by remember(original.code) { mutableStateOf(original.address) }
        var amount by remember(original.code) { mutableStateOf(original.amount) }
        var item by remember(original.code) { mutableStateOf(original.item) }
        var services by remember(original.code) { mutableStateOf(original.tags.joinToString(" ")) }
        AlertDialog(
            onDismissRequest = { editOrder = null },
            title = { Text("Sửa ${original.code}") },
            text = {
                Column(
                    Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(shop, { shop = it }, label = { Text("Tên Shop") }, singleLine = true)
                    OutlinedTextField(customer, { customer = it }, label = { Text("Tên khách") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("SĐT") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Địa chỉ") })
                    OutlinedTextField(amount, { amount = it }, label = { Text("COD") }, singleLine = true)
                    OutlinedTextField(item, { item = it }, label = { Text("Hàng hóa") })
                    OutlinedTextField(services, { services = it }, label = { Text("Dịch vụ") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = services.split(Regex("[\\\\s,;]+" )).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    vm.updateOrder(original.copy(
                        shop = shop,
                        customer = customer,
                        phone = phone,
                        address = address,
                        amount = amount,
                        item = item,
                        tags = tags
                    ))
                    editOrder = null
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { editOrder = null }) { Text("HỦY") } }
        )
    }
'''
    s = s[:start] + editor + s[end:]

# 3) Single MVĐ trước đây bị mất hẳn hình tròn route. Truyền routeStt xuống card
# và luôn hiển thị vòng tròn chờ (rỗng khi chưa Tạo tuyến).
s = s.replace('''        SingleOrderDetailCard(
            order = primary,
            delivered = delivered,''','''        SingleOrderDetailCard(
            order = primary,
            routeStt = routeStt,
            hasRealCoordinate = deliveryGroupHasCoordinate(group),
            delivered = delivered,''',1)

s = s.replace('''private fun SingleOrderDetailCard(
    order: Order,
    delivered: Boolean,''','''private fun SingleOrderDetailCard(
    order: Order,
    routeStt: Int?,
    hasRealCoordinate: Boolean,
    delivered: Boolean,''',1)

old_row = '''            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(order.status.ifBlank { if (delivered) "Đã giao" else "—" }, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)'''
new_row = '''            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!delivered) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { },
                        contentAlignment = Alignment.Center
                    ) { RouteStateCircle(routeStt, hasRealCoordinate, false) }
                    Spacer(Modifier.width(7.dp))
                }
                Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(order.status.ifBlank { if (delivered) "Đã giao" else "—" }, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)'''
# Replace only within SingleOrderDetailCard, not grouped detail.
single_pos = s.find('private fun SingleOrderDetailCard(')
if single_pos >= 0:
    row_pos = s.find(old_row, single_pos)
    if row_pos >= 0:
        s = s[:row_pos] + s[row_pos:].replace(old_row, new_row, 1)

# 4) Dọn đoạn dialog STT cũ đã bị vô hiệu hóa bằng if(false); đây là dead code,
# không liên quan tính năng đang chạy.
s = re.sub(r'\n    if \(false\) editMarker\?\.let \{ marker ->.*?\n    \}\n\n    if \(confirmSave\) AlertDialog\(', '\n\n    if (confirmSave) AlertDialog(', s, count=1, flags=re.S)

p.write_text(s)
print('final order editor + waiting circle cleanup applied')
