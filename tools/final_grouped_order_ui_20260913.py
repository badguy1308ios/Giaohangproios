from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

MARKER = '// FINAL_GROUPED_ORDER_UI_V1'
if MARKER in s:
    print('final grouped order UI already integrated; no changes required')
    raise SystemExit(0)

start = s.index('@Composable\nfun OrderListScreen(')
end = s.index('\n@Composable\nfun OrderInfoRow(', start)

new_block = r'''// FINAL_GROUPED_ORDER_UI_V1
@Composable
fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val allGroups = buildDeliveryGroups(vm.orders, vm.customers)
    val activeGroups = allGroups.filterNot { g -> g.orders.all { it.status.equals("Đã giao", true) } }
    val deliveredGroups = allGroups.filter { g -> g.orders.all { it.status.equals("Đã giao", true) } }
    var keyword by remember { mutableStateOf("") }
    val q = keyword.trim()
    fun matches(g: DeliveryGroup): Boolean = q.isBlank() ||
        g.orders.any { o ->
            o.code.contains(q, true) || o.shop.contains(q, true) || o.customer.contains(q, true) ||
                o.phone.contains(q, true) || o.address.contains(q, true) || o.item.contains(q, true) ||
                o.amount.contains(q, true) || o.status.contains(q, true)
        } || (g.customer?.name?.contains(q, true) == true) ||
        (g.customer?.phone?.contains(q, true) == true)

    val visibleGroups = activeGroups.filter(::matches) + deliveredGroups.filter(::matches)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val index = visibleGroups.indexOfFirst { group -> group.orders.any { it.code == code } }
        if (index >= 0) listState.scrollToItem(index)
        onFocusConsumed()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        Spacer(Modifier.height(6.dp))
        SearchBox(keyword, { keyword = it }, { keyword = "" }) {}
        Spacer(Modifier.height(6.dp))
        Text(
            "Tổng số: ${vm.orders.size} đơn",
            color = Navy,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 62.dp)
        ) {
            items(visibleGroups, key = { it.key }) { group ->
                val delivered = group.orders.all { it.status.equals("Đã giao", true) }
                val routeStt = if (delivered) null else activeGroups.indexOfFirst { it.key == group.key }
                    .takeIf { it >= 0 }?.plus(1)

                if (group.orders.size > 1) {
                    MultiOrderCustomerGroup(
                        routeStt = routeStt,
                        group = group,
                        delivered = delivered,
                        onNumberClick = { group.orders.firstOrNull()?.let(onNumberClick) },
                        onCustomerClick = onCustomerClick,
                        onDelivered = { group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) } }
                    )
                } else {
                    val order = group.orders.first()
                    NormalSingleOrderCard(
                        routeStt = routeStt,
                        order = order,
                        customer = group.customer,
                        delivered = delivered,
                        onNumberClick = { onNumberClick(order) },
                        onCustomerClick = { onCustomerClick(order) },
                        onDelivered = { vm.markDeliveredGroup(order.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiOrderCustomerGroup(
    routeStt: Int?,
    group: DeliveryGroup,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: (Order) -> Unit,
    onDelivered: () -> Unit
) {
    val primary = group.orders.first()
    val phone = (group.customer?.phone ?: primary.phone).filter(Char::isDigit)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (delivered) Color(0xFFE4F5E8) else OrangeLight
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (delivered) Color(0xFF9DCEA8) else Border
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (routeStt != null) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).clickable { onNumberClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            NumberCircle(routeStt, selected = true)
                        }
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Đã giao",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${group.orders.size} đơn",
                        color = Navy,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Tổng ${groupMoneyText(group)}",
                        color = MoneyGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                SharedGroupActions(phone = phone, onDelivered = onDelivered)
            }
        }

        group.orders.forEach { order ->
            GroupedOrderDetailCard(
                order = order,
                delivered = delivered,
                onCustomerClick = { onCustomerClick(order) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupedOrderDetailCard(
    order: Order,
    delivered: Boolean,
    onCustomerClick: () -> Unit
) {
    val context = LocalContext.current
    var infoPopup by remember(order.code) { mutableStateOf<Pair<String, String>?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (delivered) Color(0xFF9DCEA8) else Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.code,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(order.code))
                            Toast.makeText(context, "Đã copy MVĐ ${order.code}", Toast.LENGTH_SHORT).show()
                        }
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    order.status.ifBlank { "—" },
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(order.amount, color = MoneyGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer }) {
                infoPopup = "SHOP" to order.shop.ifBlank { order.customer }
            }
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn, order.address) {
                infoPopup = "ĐỊA CHỈ" to order.address
            }
            OrderInfoRow(Icons.Default.Inventory2, order.item) {
                infoPopup = "HÀNG HÓA" to order.item
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                order.tags.forEach { Tag(it) }
            }
        }
    }

    infoPopup?.let { (title, value) ->
        AlertDialog(
            onDismissRequest = { infoPopup = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(value.ifBlank { "—" }) },
            confirmButton = { TextButton(onClick = { infoPopup = null }) { Text("ĐÓNG") } }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NormalSingleOrderCard(
    routeStt: Int?,
    order: Order,
    customer: Customer?,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit
) {
    val context = LocalContext.current
    var infoPopup by remember(order.code) { mutableStateOf<Pair<String, String>?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val phone = (customer?.phone ?: order.phone).filter(Char::isDigit)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (delivered) Color(0xFF9DCEA8) else Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (routeStt != null) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { onNumberClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        NumberCircle(routeStt, selected = true)
                    }
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Đã giao",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    order.code,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(order.code))
                            Toast.makeText(context, "Đã copy MVĐ ${order.code}", Toast.LENGTH_SHORT).show()
                        }
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    order.status.ifBlank { "—" },
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(order.amount, color = MoneyGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer }) {
                infoPopup = "SHOP" to order.shop.ifBlank { order.customer }
            }
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn, order.address) {
                infoPopup = "ĐỊA CHỈ" to order.address
            }
            OrderInfoRow(Icons.Default.Inventory2, order.item) {
                infoPopup = "HÀNG HÓA" to order.item
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                order.tags.forEach { Tag(it) }
            }
            Spacer(Modifier.height(6.dp))
            SharedGroupActions(phone = phone, onDelivered = onDelivered)
        }
    }

    infoPopup?.let { (title, value) ->
        AlertDialog(
            onDismissRequest = { infoPopup = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(value.ifBlank { "—" }) },
            confirmButton = { TextButton(onClick = { infoPopup = null }) { Text("ĐÓNG") } }
        )
    }
}

@Composable
private fun RowScope.SharedGroupActions(
    phone: String,
    onDelivered: () -> Unit
) {
    val context = LocalContext.current
    ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, onClick = onDelivered)
    ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
    ActionButton("Zalo", Icons.Default.Chat, onClick = {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/$phone")))
        }
    })
    ActionButton("SMS", Icons.Default.Sms, onClick = {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$phone")))
        }
    })
    ActionButton("Gọi", Icons.Default.Call, onClick = {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
        }
    })
}
'''

s = s[:start] + new_block + s[end:]
p.write_text(s)
print('final grouped order UI integrated')
