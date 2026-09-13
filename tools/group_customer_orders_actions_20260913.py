from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Shared delivery grouping helpers.
anchor='''private fun openGoogleNavigation(context: android.content.Context, point: MapPoint) {'''
helpers=r'''private fun uiNormPhone(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
        digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
        else -> digits
    }
}

private data class DeliveryGroup(val key: String, val customer: Customer?, val orders: List<Order>)

private fun buildDeliveryGroups(orders: List<Order>, customers: List<Customer>): List<DeliveryGroup> {
    val customerByPhone = mutableMapOf<String, Customer>()
    customers.forEach { c ->
        (listOf(c.phone) + c.extraPhones.map { it.number }).forEach { raw ->
            uiNormPhone(raw).takeIf(String::isNotBlank)?.let { customerByPhone[it] = c }
        }
    }
    val grouped = linkedMapOf<String, MutableList<Order>>()
    val customerForKey = mutableMapOf<String, Customer?>()
    orders.forEach { order ->
        val customer = customerByPhone[uiNormPhone(order.phone)]
        val key = customer?.let { "C:${it.id}" } ?: "O:${order.code}"
        grouped.getOrPut(key) { mutableListOf() }.add(order)
        customerForKey[key] = customer
    }
    return grouped.map { (key, list) -> DeliveryGroup(key, customerForKey[key], list) }
}

private fun orderMoneyValue(raw: String): Long = raw.filter(Char::isDigit).toLongOrNull() ?: 0L
private fun groupMoneyText(group: DeliveryGroup): String = fmtMoney(group.orders.sumOf { orderMoneyValue(it.amount) }) + "đ"
private fun groupRepresentative(group: DeliveryGroup): Order {
    val first = group.orders.first()
    val c = group.customer
    return first.copy(
        customer = c?.name ?: first.customer,
        phone = c?.phone ?: first.phone,
        address = c?.address?.takeIf(String::isNotBlank) ?: first.address,
        latitude = c?.latitude?.takeIf(String::isNotBlank) ?: first.latitude,
        longitude = c?.longitude?.takeIf(String::isNotBlank) ?: first.longitude,
        amount = groupMoneyText(group),
        item = if (group.orders.size > 1) "${group.orders.size} MVĐ" else first.item
    )
}

'''
if 'private data class DeliveryGroup' not in s:
    s=s.replace(anchor,helpers+anchor,1)

# Replace MapScreen so one customer = one route stop/STT.
start=s.index('@Composable\nfun MapScreen(')
end=s.index('\n@Composable\nprivate fun BaseMapScreen(', start)
new_map=r'''@Composable
fun MapScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenOrder: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activeGroups = buildDeliveryGroups(vm.orders.filterNot { it.status.equals("Đã giao", true) }, vm.customers)
    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember(activeGroups.map { it.key }) { mutableStateOf(activeGroups.map { it.key }) }
    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("STT,MVĐ\n")
                activeGroups.forEachIndexed { i, g -> append("${i + 1},${csvCell(g.orders.joinToString(" ") { it.code })}\n") }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context, "Đã xuất ${activeGroups.size} điểm giao", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context, "Không xuất được STT", Toast.LENGTH_LONG).show() }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val codes = text.lineSequence().drop(1).mapNotNull { line -> line.substringAfter(',', "").trim().trim('"').split(' ').firstOrNull()?.takeIf(String::isNotBlank) }.toList()
            val byRep = activeGroups.associateBy { it.orders.first().code }
            val ordered = codes.mapNotNull { byRep[it] }.flatMap { it.orders.map(Order::code) }
            val remaining = activeGroups.filterNot { it.orders.first().code in codes }.flatMap { it.orders.map(Order::code) }
            vm.reorderOrders(ordered + remaining)
        }.onFailure { Toast.makeText(context, "Không đọc được file STT", Toast.LENGTH_LONG).show() }
    }

    val orderedGroups = if (editing) draft.mapNotNull { key -> activeGroups.firstOrNull { it.key == key } } else activeGroups
    val displayOrders = orderedGroups.map(::groupRepresentative)

    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        editingStt = editing,
        focusOrderCode = focusOrderCode,
        onFocusConsumed = onFocusConsumed,
        onOpenOrder = onOpenOrder,
        onCreateRoute = {
            val sortedGroups = activeGroups.sortedWith(compareBy<DeliveryGroup> {
                val r = groupRepresentative(it); r.latitude.toDoubleOrNull() ?: 999.0
            }.thenBy {
                val r = groupRepresentative(it); r.longitude.toDoubleOrNull() ?: 999.0
            })
            vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
            Toast.makeText(context, "Đã tạo tuyến theo ${sortedGroups.size} điểm giao", Toast.LENGTH_SHORT).show()
        },
        onEditRoute = {
            draft = activeGroups.map { it.key }
            editing = true
            Toast.makeText(context, "Chạm STT để nhập số mới trực tiếp", Toast.LENGTH_SHORT).show()
        },
        onSaveRoute = { confirmSave = true },
        onEditStt = { marker ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
            }
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },
        onImportStt = { importLauncher.launch("text/*") }
    )

    editMarker?.let { marker ->
        val maxStt = draft.size.coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { editMarker = null },
            title = { Text("Đổi STT") },
            text = { OutlinedTextField(value=editNumberText,onValueChange={editNumberText=it.filter(Char::isDigit).take(4)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),label={Text("STT mới 1-$maxStt")}) },
            confirmButton = { TextButton(onClick = {
                val target = editNumberText.toIntOrNull()?.coerceIn(1,maxStt)
                if(target!=null){
                    val groupKey = activeGroups.firstOrNull { it.orders.first().code == marker.order.code }?.key
                    val current = groupKey?.let(draft::indexOf) ?: -1
                    if(current>=0){ val next=draft.toMutableList(); val moving=next.removeAt(current); next.add((target-1).coerceIn(0,next.size),moving); draft=next }
                    editMarker=null
                }
            }) { Text("ÁP DỤNG") } },
            dismissButton = { TextButton(onClick={editMarker=null}){Text("HỦY")} }
        )
    }

    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave=false },
        title = { Text("Lưu STT tuyến") },
        text = { Text("Lưu thứ tự ${draft.size} điểm giao?") },
        confirmButton = { TextButton(onClick={
            val groupByKey=activeGroups.associateBy{it.key}
            vm.reorderOrders(draft.mapNotNull(groupByKey::get).flatMap{it.orders.map(Order::code)})
            confirmSave=false; editing=false
        }){Text("LƯU")} },
        dismissButton = { TextButton(onClick={confirmSave=false}){Text("HỦY")} }
    )
}
'''
s=s[:start]+new_map+s[end:]

# Replace order list + card with grouped UI/actions. Search keeps original route STT.
start=s.index('@Composable\nfun OrderListScreen(')
end=s.index('\n@Composable\nfun OrderInfoRow(', start)
new_orders=r'''@Composable
fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val context = LocalContext.current
    val allGroups = buildDeliveryGroups(vm.orders, vm.customers)
    val activeGroups = allGroups.filterNot { g -> g.orders.all { it.status.equals("Đã giao", true) } }
    val deliveredGroups = allGroups.filter { g -> g.orders.all { it.status.equals("Đã giao", true) } }
    var keyword by remember { mutableStateOf("") }
    val q = keyword.trim()
    fun matches(g: DeliveryGroup): Boolean = q.isBlank() || g.orders.any { o -> o.code.contains(q,true)||o.customer.contains(q,true)||o.phone.contains(q,true)||o.address.contains(q,true) } || (g.customer?.name?.contains(q,true)==true)
    val visibleGroups = (activeGroups.filter(::matches) + deliveredGroups.filter(::matches))
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code=focusOrderCode ?: return@LaunchedEffect
        val i=visibleGroups.indexOfFirst { g -> g.orders.any { it.code==code } }
        if(i>=0) listState.scrollToItem(i)
        onFocusConsumed()
    }

    Column(Modifier.fillMaxSize().padding(horizontal=6.dp)) {
        Spacer(Modifier.height(6.dp))
        SearchBox(keyword,{keyword=it},{keyword=""}) {}
        Spacer(Modifier.height(6.dp))
        Text("${activeGroups.size} điểm giao • ${vm.orders.size} MVĐ",color=Navy,fontSize=18.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyColumn(state=listState,verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=62.dp)) {
            items(visibleGroups,key={it.key}) { group ->
                val delivered=group.orders.all { it.status.equals("Đã giao",true) }
                val routeStt=if(delivered) null else activeGroups.indexOfFirst { it.key==group.key }.takeIf { it>=0 }?.plus(1)
                DeliveryGroupCard(
                    routeStt=routeStt,
                    group=group,
                    delivered=delivered,
                    onNumberClick={ group.orders.firstOrNull()?.let(onNumberClick) },
                    onCustomerClick={ group.orders.firstOrNull()?.let(onCustomerClick) },
                    onDelivered={ group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) } }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DeliveryGroupCard(
    routeStt: Int?,
    group: DeliveryGroup,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit
) {
    val context=LocalContext.current
    val primary=group.orders.first()
    val customer=group.customer
    val phone=(customer?.phone ?: primary.phone).filter(Char::isDigit)
    Card(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(14.dp),
        colors=CardDefaults.cardColors(containerColor=if(delivered) Color(0xFFE4F5E8) else Color.White),
        border=androidx.compose.foundation.BorderStroke(1.dp,if(delivered) Color(0xFF9DCEA8) else Border),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)
    ) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(routeStt!=null) Box(Modifier.size(30.dp).clip(CircleShape).clickable{onNumberClick()},contentAlignment=Alignment.Center){ NumberCircle(routeStt,true) }
                else Icon(Icons.Default.CheckCircle,"Đã giao",tint=Color(0xFF2E7D32),modifier=Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(customer?.name ?: primary.customer,color=Navy,fontSize=15.sp,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${group.orders.size} MVĐ",color=TextGray,fontSize=11.sp)
                }
                Text(groupMoneyText(group),color=MoneyGreen,fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            group.orders.forEach { o ->
                Row(Modifier.fillMaxWidth().padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(o.code,Modifier.weight(1f),color=Navy,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    Text(o.amount,color=MoneyGreen,fontSize=12.sp)
                }
            }
            OrderInfoRow(Icons.Default.Person,"${customer?.name ?: primary.customer} - ${customer?.phone ?: primary.phone}",onClick=onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn,customer?.address?.takeIf(String::isNotBlank) ?: primary.address)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                ActionButton("Đã giao",Icons.Default.CheckCircle,filled=true,onClick=onDelivered)
                ActionButton("Bank",Icons.Default.AccountBalance,onClick={})
                ActionButton("Zalo",Icons.Default.Chat,onClick={
                    if(phone.isNotBlank()) runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://zalo.me/$phone"))) }
                })
                ActionButton("SMS",Icons.Default.Sms,onClick={
                    if(phone.isNotBlank()) runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:$phone"))) }
                })
                ActionButton("Gọi",Icons.Default.Call,onClick={
                    if(phone.isNotBlank()) runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL,android.net.Uri.parse("tel:$phone"))) }
                })
            }
        }
    }
}
'''
s=s[:start]+new_orders+s[end:]

# ActionButton accepts click callback.
s=s.replace('fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = false)',
            'fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = false, onClick: () -> Unit = {})')
s=s.replace('modifier = Modifier\n            .height(34.dp)', 'modifier = Modifier\n            .height(34.dp)\n            .clickable { onClick() }',1)

# ViewModel action: mark all MVĐ that belong to same customer ID as delivered.
vm_anchor='''    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }'''
vm_method=r'''    fun markDeliveredGroup(code: String) {
        val base = orderState.firstOrNull { it.code == code } ?: return
        val customer = findCustomerByPhone(base.phone)
        if (customer == null) {
            val i = orderState.indexOfFirst { it.code == code }
            if (i >= 0) orderState[i] = orderState[i].copy(status = "Đã giao")
        } else {
            val keys = (listOf(customer.phone) + customer.extraPhones.map { it.number }).map(::normalizeCustomerPhone).filter(String::isNotBlank).toSet()
            orderState.indices.forEach { i ->
                if (normalizeCustomerPhone(orderState[i].phone) in keys) orderState[i] = orderState[i].copy(status = "Đã giao")
            }
        }
        savePersistentData()
    }

'''
if 'fun markDeliveredGroup(' not in s:
    s=s.replace(vm_anchor,vm_anchor+'\n\n'+vm_method,1)

p.write_text(s)
print('customer delivery grouping/actions patch applied')
