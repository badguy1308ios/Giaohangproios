from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

start = s.index('@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n@Composable\nprivate fun DeliveryGroupCard(')
end = s.index('\n@Composable\nfun OrderInfoRow(', start)

new = r'''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DeliveryGroupCard(
    routeStt: Int?,
    group: DeliveryGroup,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit
) {
    val context = LocalContext.current
    val primary = group.orders.first()
    val customer = group.customer
    val phone = (customer?.phone ?: primary.phone).filter(Char::isDigit)

    fun openZalo() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/$phone")))
        }
    }
    fun openSms() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$phone")))
        }
    }
    fun openCall() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
        }
    }

    // Chỉ Customer ID có từ 2 MVĐ trở lên mới có phần đầu nhóm.
    if (group.orders.size > 1) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF9DCEA8) else Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // Phần dùng chung: STT - số đơn - tổng tiền.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (routeStt != null) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() },
                            contentAlignment = Alignment.Center
                        ) { NumberCircle(routeStt, selected = true) }
                    } else {
                        Icon(Icons.Default.CheckCircle, "Đã giao", tint = Color(0xFF2E7D32), modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text("${group.orders.size} đơn", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  •  Tổng COD ", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(groupMoneyText(group), color = MoneyGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }

                // Cụm nút thao tác dùng chung cho tất cả MVĐ trong Customer ID này.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, onClick = onDelivered)
                    ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
                    ActionButton("Zalo", Icons.Default.Chat, onClick = { openZalo() })
                    ActionButton("SMS", Icons.Default.Sms, onClick = { openSms() })
                    ActionButton("Gọi", Icons.Default.Call, onClick = { openCall() })
                }

                // Mỗi đơn vẫn giữ nguyên cấu trúc chi tiết riêng như giao diện cũ.
                group.orders.forEachIndexed { index, order ->
                    if (index > 0) HorizontalDivider(color = Border)
                    GroupedOrderDetail(
                        order = order,
                        delivered = delivered,
                        onCustomerClick = onCustomerClick
                    )
                }
            }
        }
    } else {
        // Customer ID chỉ có 1 MVĐ: hiển thị bình thường, không có phần đầu nhóm.
        SingleOrderDetailCard(
            order = primary,
            delivered = delivered,
            onCustomerClick = onCustomerClick,
            onDelivered = onDelivered,
            onZalo = { openZalo() },
            onSms = { openSms() },
            onCall = { openCall() }
        )
    }
}

@Composable
private fun GroupedOrderDetail(
    order: Order,
    delivered: Boolean,
    onCustomerClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(order.status.ifBlank { if (delivered) "Đã giao" else "—" }, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(order.amount, color = MoneyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
        OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
        OrderInfoRow(Icons.Default.LocationOn, order.address)
        OrderInfoRow(Icons.Default.Inventory2, order.item)
        if (order.tags.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { order.tags.forEach { Tag(it) } }
        }
    }
}

@Composable
private fun SingleOrderDetailCard(
    order: Order,
    delivered: Boolean,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit,
    onZalo: () -> Unit,
    onSms: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF9DCEA8) else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(order.status.ifBlank { if (delivered) "Đã giao" else "—" }, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(order.amount, color = MoneyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn, order.address)
            OrderInfoRow(Icons.Default.Inventory2, order.item)
            if (order.tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { order.tags.forEach { Tag(it) } }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, onClick = onDelivered)
                ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
                ActionButton("Zalo", Icons.Default.Chat, onClick = onZalo)
                ActionButton("SMS", Icons.Default.Sms, onClick = onSms)
                ActionButton("Gọi", Icons.Default.Call, onClick = onCall)
            }
        }
    }
}
'''

s = s[:start] + new + s[end:]
p.write_text(s)
print('approved grouped order UI applied')
