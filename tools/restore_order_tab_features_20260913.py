from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# 1) Group by Customer ID when found; otherwise identical normalized phone numbers
# still form one delivery group. This guarantees same SĐT cannot split into separate cards.
old_group = '''private fun buildDeliveryGroups(orders: List<Order>, customers: List<Customer>): List<DeliveryGroup> {
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
'''
new_group = '''private fun buildDeliveryGroups(orders: List<Order>, customers: List<Customer>): List<DeliveryGroup> {
    val customerByPhone = mutableMapOf<String, Customer>()
    customers.forEach { c ->
        (listOf(c.phone) + c.extraPhones.map { it.number }).forEach { raw ->
            uiNormPhone(raw).takeIf(String::isNotBlank)?.let { customerByPhone[it] = c }
        }
    }
    val grouped = linkedMapOf<String, MutableList<Order>>()
    val customerForKey = mutableMapOf<String, Customer?>()
    orders.forEach { order ->
        val normalizedPhone = uiNormPhone(order.phone)
        val customer = customerByPhone[normalizedPhone]
        val key = when {
            customer != null -> "C:${customer.id}"
            normalizedPhone.isNotBlank() -> "P:$normalizedPhone"
            else -> "O:${order.code}"
        }
        grouped.getOrPut(key) { mutableListOf() }.add(order)
        customerForKey[key] = customer
    }
    return grouped.map { (key, list) -> DeliveryGroup(key, customerForKey[key], list) }
}
'''
if old_group in s:
    s = s.replace(old_group, new_group, 1)

# 2) Restore the order-tab tools that were lost when grouped UI replaced OrderListScreen.
start = s.index('@Composable\nfun OrderListScreen(')
end = s.index('\n@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n@Composable\nprivate fun DeliveryGroupCard(', start)
order_screen = r'''@Composable
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
    var showTools by remember { mutableStateOf(false) }
    var editPicker by remember { mutableStateOf(false) }
    var editOrder by remember { mutableStateOf<Order?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val q = keyword.trim()

    fun matches(g: DeliveryGroup): Boolean = q.isBlank() ||
        g.orders.any { o ->
            o.code.contains(q, true) || o.customer.contains(q, true) ||
                o.phone.contains(q, true) || o.address.contains(q, true) ||
                o.shop.contains(q, true) || o.item.contains(q, true)
        } || (g.customer?.name?.contains(q, true) == true)

    val visibleGroups = activeGroups.filter(::matches) + deliveredGroups.filter(::matches)
    val filteredOrders = if (q.isBlank()) vm.orders else vm.orders.filter { o ->
        o.code.contains(q, true) || o.customer.contains(q, true) || o.phone.contains(q, true) ||
            o.address.contains(q, true) || o.shop.contains(q, true) || o.item.contains(q, true)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword = it }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val n = vm.importOrdersCsv(text)
            Toast.makeText(context, "Đã nhập $n đơn từ CSV", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không đọc được CSV", Toast.LENGTH_LONG).show() }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("MVĐ,Shop,SĐT,Tên khách,COD,Địa chỉ,Hàng hóa,Trạng thái,Dịch vụ\n")
                vm.orders.forEach { o ->
                    append(listOf(o.code,o.shop,o.phone,o.customer,o.amount,o.address,o.item,o.status,o.tags.joinToString(" ")).joinToString(",") { csvCell(it) }).append('\n')
                }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context, "Đã xuất ${vm.orders.size} đơn", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context, "Không xuất được danh sách", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.scrollToItem(i)
        onFocusConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Spacer(Modifier.height(6.dp))
            SearchBox(keyword, { keyword = it }, { keyword = "" }) {
                scanLauncher.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")
                    setBeepEnabled(false)
                    setCaptureActivity(PortraitCaptureActivity::class.java)
                    setOrientationLocked(true)
                    setBarcodeImageEnabled(false)
                })
            }
            Spacer(Modifier.height(6.dp))

            if (deleteMode) {
                val allVisibleSelected = filteredOrders.isNotEmpty() && filteredOrders.all { it.code in selected }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allVisibleSelected,
                        onCheckedChange = { all -> selected = if (all) filteredOrders.map { it.code }.toSet() else emptySet() }
                    )
                    Text("Chọn tất cả", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                    TextButton(onClick = { deleteMode = false; selected = emptySet() }) { Text("HỦY") }
                    Button(
                        onClick = { if (selected.isNotEmpty()) confirmDelete = true },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("XÓA (${selected.size})")
                    }
                }
            } else {
                Text("${activeGroups.size} điểm giao • ${vm.orders.size} MVĐ", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))

            if (deleteMode) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(bottom = 62.dp)
                ) {
                    items(filteredOrders, key = { it.code }) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = order.code in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + order.code else selected - order.code
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(order.code, fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                                    Text("${order.customer} • ${order.phone}", color = TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(order.amount, color = MoneyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 62.dp)
                ) {
                    items(visibleGroups, key = { it.key }) { group ->
                        val delivered = group.orders.all { it.status.equals("Đã giao", true) }
                        val routeStt = if (delivered) null else activeGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)
                        DeliveryGroupCard(
                            routeStt = routeStt,
                            group = group,
                            delivered = delivered,
                            onNumberClick = { group.orders.firstOrNull()?.let(onNumberClick) },
                            onCustomerClick = { group.orders.firstOrNull()?.let(onCustomerClick) },
                            onDelivered = { group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) } }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showTools = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(44.dp),
            containerColor = Orange
        ) { Icon(Icons.Default.Edit, "Công cụ đơn", tint = Color.White) }

        DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }, modifier = Modifier.align(Alignment.BottomStart)) {
            DropdownMenuItem(
                text = { Text("Nhập danh sách đơn") },
                leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                onClick = { showTools = false; importLauncher.launch("text/*") }
            )
            DropdownMenuItem(
                text = { Text("Xuất danh sách đơn") },
                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                onClick = { showTools = false; exportLauncher.launch("giaohangpro_orders.csv") }
            )
            DropdownMenuItem(
                text = { Text("Sửa đơn hàng") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { showTools = false; editPicker = true }
            )
            DropdownMenuItem(
                text = { Text("Xóa đơn hàng") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { showTools = false; selected = emptySet(); deleteMode = true }
            )
        }
    }

    if (editPicker) AlertDialog(
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

    editOrder?.let { original ->
        var customer by remember(original.code) { mutableStateOf(original.customer) }
        var phone by remember(original.code) { mutableStateOf(original.phone) }
        var address by remember(original.code) { mutableStateOf(original.address) }
        var amount by remember(original.code) { mutableStateOf(original.amount) }
        AlertDialog(
            onDismissRequest = { editOrder = null },
            title = { Text("Sửa ${original.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(customer, { customer = it }, label = { Text("Tên khách") })
                    OutlinedTextField(phone, { phone = it }, label = { Text("SĐT") })
                    OutlinedTextField(address, { address = it }, label = { Text("Địa chỉ") })
                    OutlinedTextField(amount, { amount = it }, label = { Text("COD") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateOrder(original.copy(customer = customer, phone = phone, address = address, amount = amount))
                    editOrder = null
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { editOrder = null }) { Text("HỦY") } }
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xóa đơn hàng") },
        text = { Text("Xác nhận xóa ${selected.size} đơn đã chọn?") },
        confirmButton = {
            TextButton(onClick = {
                vm.deleteOrders(selected)
                selected = emptySet()
                confirmDelete = false
                deleteMode = false
            }) { Text("XÓA", color = Color(0xFFE21B1B)) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("HỦY") } }
    )
}
'''
s = s[:start] + order_screen + s[end:]

# 3) Restore ViewModel helpers used by the order-tab tools.
marker = '    fun updateOrder(updated: Order) {'
if 'fun importOrdersCsv(text: String)' not in s:
    pos = s.index(marker)
    helpers = r'''    fun importOrdersCsv(text: String): Int {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return 0
        fun cells(line: String): List<String> {
            val out = mutableListOf<String>(); val b = StringBuilder(); var quoted = false; var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') { b.append('"'); i++ } else quoted = !quoted
                } else if (c == ',' && !quoted) { out += b.toString().trim(); b.setLength(0) } else b.append(c)
                i++
            }
            out += b.toString().trim(); return out
        }
        val h = cells(lines.first()).map {
            java.text.Normalizer.normalize(it.lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace("đ", "d")
        }
        fun idx(vararg keys: String) = h.indexOfFirst { x -> keys.any { x.contains(it) } }
        val ic = idx("mvd", "ma van don"); if (ic < 0) return 0
        val ishop = idx("shop"); val ip = idx("sdt", "so dien thoai"); val iname = idx("ten khach")
        val icod = idx("cod"); val ia = idx("dia chi"); val ii = idx("hang hoa"); val ist = idx("trang thai"); val isv = idx("dich vu")
        var n = 0
        lines.drop(1).forEach { line ->
            val c = cells(line)
            fun g(i: Int) = if (i >= 0 && i < c.size) c[i] else ""
            val code = g(ic).trim()
            if (code.isNotBlank()) {
                val o = Order(
                    code, g(iname), g(ip), g(ia), g(ii), g(icod),
                    g(isv).split(' ', ';', ',', '|').map(String::trim).filter(String::isNotBlank),
                    shop = g(ishop), status = g(ist).ifBlank { "Chưa giao" }
                )
                val k = orderState.indexOfFirst { it.code.equals(code, true) }
                if (k >= 0) orderState[k] = o else orderState.add(o)
                n++
            }
        }
        if (n > 0) savePersistentData()
        return n
    }

    fun deleteOrders(codes: Set<String>) {
        if (orderState.removeAll { it.code in codes }) savePersistentData()
    }

'''
    s = s[:pos] + helpers + s[pos:]

# 4) Group-level Đã giao must also affect every order sharing the same normalized phone,
# even if that phone was not linked to a saved Customer record yet.
old_mark = '''    fun markDeliveredGroup(code: String) {
        val base = orderState.firstOrNull { it.code == code } ?: return
        val customer = findCustomerByPhone(base.phone)
        if (customer == null) {
            val i = orderState.indexOfFirst { it.code == code }
            if (i >= 0) orderState[i] = orderState[i].copy(status = "Đã giao")
        } else {
            val customerPhones = (listOf(customer.phone) + customer.extraPhones.map { it.number })
                .map(::normalizeCustomerPhone)
                .filter(String::isNotBlank)
                .toSet()
            orderState.indices.forEach { i ->
                if (normalizeCustomerPhone(orderState[i].phone) in customerPhones) {
                    orderState[i] = orderState[i].copy(status = "Đã giao")
                }
            }
        }
        savePersistentData()
    }
'''
new_mark = '''    fun markDeliveredGroup(code: String) {
        val base = orderState.firstOrNull { it.code == code } ?: return
        val customer = findCustomerByPhone(base.phone)
        val basePhone = normalizeCustomerPhone(base.phone)
        val customerPhones = if (customer != null) {
            (listOf(customer.phone) + customer.extraPhones.map { it.number })
                .map(::normalizeCustomerPhone).filter(String::isNotBlank).toSet()
        } else {
            setOf(basePhone).filter(String::isNotBlank).toSet()
        }
        orderState.indices.forEach { i ->
            if (normalizeCustomerPhone(orderState[i].phone) in customerPhones) {
                orderState[i] = orderState[i].copy(status = "Đã giao")
            }
        }
        savePersistentData()
    }
'''
if old_mark in s:
    s = s.replace(old_mark, new_mark, 1)

p.write_text(s)
print('restored order-tab tools and same-phone grouping')
