from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

if 'import androidx.compose.foundation.combinedClickable' not in s:
    s=s.replace('import androidx.compose.foundation.clickable\n','import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n',1)

s=s.replace('''    var importedCount by remember { mutableIntStateOf(0) }
    var importedCount by remember { mutableIntStateOf(0) }
''','''    var importedCount by remember { mutableIntStateOf(0) }
''')

needle='    var importedCount by remember { mutableIntStateOf(0) }\n    val csvImportLauncher'
replacement='''    var importedCount by remember { mutableIntStateOf(0) }
    val vtmanScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
            val currentCodes = waybills.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
            val alreadyHasInfo = vm.orders.any { it.code.trim().equals(code, ignoreCase = true) }
            when {
                alreadyHasInfo -> Toast.makeText(context, "Mã $code đã có thông tin trong Chi tiết đơn - bỏ qua", Toast.LENGTH_LONG).show()
                currentCodes.any { it.equals(code, ignoreCase = true) } -> Toast.makeText(context, "Mã $code đã được quét", Toast.LENGTH_SHORT).show()
                else -> {
                    val updatedCodes = currentCodes + code
                    waybills = updatedCodes.joinToString("\\n")
                    com.example.giaohangpro.vtman.VtmanQueueController.load(updatedCodes)
                    importedCount = 0
                    snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                    Toast.makeText(context, "Đã thêm MVĐ $code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val csvImportLauncher'''
if needle in s and 'val vtmanScanLauncher' not in s:
    s=s.replace(needle,replacement,1)

order_marker='''@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {'''
order_fixed='''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {'''
if order_marker in s and order_fixed not in s:
    s=s.replace(order_marker,order_fixed,1)

ctx_marker='''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {
    Card('''
ctx_fixed='''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {
    val context = LocalContext.current
    Card('''
if ctx_marker in s:
    s=s.replace(ctx_marker,ctx_fixed,1)

s=s.replace('''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable''','''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable''')

s=s.replace('''            if (records.size != importedCount) { vm.importVtmanRecords(records); importedCount = records.size }''','''            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                importedCount = records.size
            }''',1)

oldtext='''                Text(
                    order.code,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )'''
newtext='''                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
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
                )'''
if oldtext in s:
    s=s.replace(oldtext,newtext,1)

old_editor='''        var customer by remember(original.code){mutableStateOf(original.customer)}; var phone by remember(original.code){mutableStateOf(original.phone)}; var address by remember(original.code){mutableStateOf(original.address)}; var amount by remember(original.code){mutableStateOf(original.amount)}
        AlertDialog(onDismissRequest={editOrder=null},title={Text("Sửa ${original.code}")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){OutlinedTextField(customer,{customer=it},label={Text("Tên khách")});OutlinedTextField(phone,{phone=it},label={Text("SĐT")});OutlinedTextField(address,{address=it},label={Text("Địa chỉ")});OutlinedTextField(amount,{amount=it},label={Text("COD")})}},confirmButton={TextButton(onClick={vm.updateOrder(original.copy(customer=customer,phone=phone,address=address,amount=amount));editOrder=null}){Text("LƯU")}},dismissButton={TextButton(onClick={editOrder=null}){Text("HỦY")}})'''
new_editor='''        var customer by remember(original.code){mutableStateOf(original.customer)}; var phone by remember(original.code){mutableStateOf(original.phone)}; var address by remember(original.code){mutableStateOf(original.address)}; var amount by remember(original.code){mutableStateOf(original.amount)}
        var shop by remember(original.code){mutableStateOf(original.shop)}; var item by remember(original.code){mutableStateOf(original.item)}; var service by remember(original.code){mutableStateOf(original.tags.joinToString(" "))}
        AlertDialog(onDismissRequest={editOrder=null},title={Text("Sửa ${original.code}")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)){OutlinedTextField(shop,{shop=it},label={Text("Tên shop")});OutlinedTextField(customer,{customer=it},label={Text("Tên khách")});OutlinedTextField(phone,{phone=it},label={Text("SĐT")});OutlinedTextField(address,{address=it},label={Text("Địa chỉ")});OutlinedTextField(item,{item=it},label={Text("Hàng hóa")});OutlinedTextField(service,{service=it},label={Text("Dịch vụ")});OutlinedTextField(amount,{amount=it},label={Text("COD")})}},confirmButton={TextButton(onClick={val tags=service.split(',', ';', ' ', '|').map(String::trim).filter(String::isNotBlank).distinct();vm.updateOrder(original.copy(shop=shop,customer=customer,phone=phone,address=address,item=item,tags=tags,amount=amount));editOrder=null}){Text("LƯU")}},dismissButton={TextButton(onClick={editOrder=null}){Text("HỦY")}})'''
if old_editor in s:
    s=s.replace(old_editor,new_editor,1)

s=s.replace('''                Text(
                    "TT505",
                    color = Navy,''','''                Text(
                    order.status.ifBlank { "—" },
                    color = Navy,''',1)

if 'var returnOrderCode by remember' not in s:
    s=s.replace('''    var formIsNew by remember { mutableStateOf(false) }
    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)''','''    var formIsNew by remember { mutableStateOf(false) }
    var returnOrderCode by remember { mutableStateOf<String?>(null) }
    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)''',1)

s=s.replace('''            AppScreen.CUSTOMER_DETAIL -> screen = AppScreen.MAIN''','''            AppScreen.CUSTOMER_DETAIL -> {
                screen = AppScreen.MAIN
                if (returnOrderCode != null) tab = Tab.ORDERS
            }''',1)

s=s.replace('''                        Tab.ORDERS -> OrderListScreen(
                            vm = vm,
                            onCustomerClick = { order ->''','''                        Tab.ORDERS -> OrderListScreen(
                            vm = vm,
                            focusOrderCode = returnOrderCode,
                            onFocusConsumed = { returnOrderCode = null },
                            onCustomerClick = { order ->
                                returnOrderCode = order.code''',1)

s=s.replace('''                        Tab.CUSTOMERS -> CustomerListScreen(
                            customers = vm.customers,
                            onCustomerClick = {
                                selectedCustomerId = it.id''','''                        Tab.CUSTOMERS -> CustomerListScreen(
                            customers = vm.customers,
                            onCustomerClick = {
                                returnOrderCode = null
                                selectedCustomerId = it.id''',1)

s=s.replace('''                onBack = { screen = AppScreen.MAIN },''','''                onBack = {
                    screen = AppScreen.MAIN
                    if (returnOrderCode != null) tab = Tab.ORDERS
                },''',1)

s=s.replace('''fun OrderListScreen(vm: MainViewModel, onCustomerClick: (Order) -> Unit) {
    val context = LocalContext.current
    val orders = vm.orders''','''fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val context = LocalContext.current
    val orders = vm.orders
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()''',1)

if 'LaunchedEffect(focusOrderCode, filteredOrders)' not in s:
    s=s.replace('''    val filteredOrders = remember(orders, keyword) { val q=keyword.trim(); if(q.isBlank()) orders else orders.filter { it.code.contains(q,true)||it.customer.contains(q,true)||it.phone.contains(q,true)||it.address.contains(q,true) } }
    Box(Modifier.fillMaxSize()) {''','''    val filteredOrders = remember(orders, keyword) { val q=keyword.trim(); if(q.isBlank()) orders else orders.filter { it.code.contains(q,true)||it.customer.contains(q,true)||it.phone.contains(q,true)||it.address.contains(q,true) } }
    LaunchedEffect(focusOrderCode, filteredOrders) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val index = filteredOrders.indexOfFirst { it.code == code }
        if (index >= 0) listState.scrollToItem(index)
        onFocusConsumed()
    }
    Box(Modifier.fillMaxSize()) {''',1)

s=s.replace('''            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp), contentPadding=PaddingValues(bottom=62.dp))''','''            LazyColumn(state=listState, verticalArrangement=Arrangement.spacedBy(6.dp), contentPadding=PaddingValues(bottom=62.dp))''',1)

s=s.replace(''').setAnchor(0.5f, 1.0f)''', ''')''')
s=s.replace('''    val height = (42 * density).toInt() // Chiều cao gồm thân và mũi nhọn.''','''    val height = (84 * density).toInt() // Nửa dưới trong suốt để tâm bitmap trùng đầu mũi nhọn.''',1)
s=s.replace('''        lineTo(width / 2f, height.toFloat())''','''        lineTo(width / 2f, 42 * density)''',1)

if 'var didInitialDriverFocus by remember' not in s:
    s=s.replace('''    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(mapView, lifecycle) {''','''    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialDriverFocus by remember { mutableStateOf(false) }

    DisposableEffect(mapView, lifecycle) {''',1)

if 'LaunchedEffect(map, driverLocation)' not in s:
    s=s.replace('''    LaunchedEffect(map, orders, driverLocation, selectedOrderNumber) {''','''    LaunchedEffect(map, driverLocation) {
        val readyMap = map
        val point = driverLocation
        if (!didInitialDriverFocus && readyMap != null && point != null) {
            readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
            didInitialDriverFocus = true
        }
    }

    LaunchedEffect(map, orders, driverLocation, selectedOrderNumber) {''',1)

# Persist all order/customer data in app-private SharedPreferences so normal APK updates keep data.
vm_marker='''// ================================================================
// 11. VIEWMODEL
// ================================================================
'''
if vm_marker in s:
    head=s.split(vm_marker,1)[0]
    persistent_vm=r'''// ================================================================
// 11. VIEWMODEL
// ================================================================
class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE)
    private val orderState = mutableStateListOf<Order>()
    val orders: List<Order> get() = orderState
    private val customerState = mutableStateListOf<Customer>()
    val customers: List<Customer> get() = customerState

    init {
        val loaded = loadPersistentData()
        if (!loaded) {
            orderState.addAll(sampleOrders)
            customerState.addAll(sampleCustomers)
            savePersistentData()
        }
    }

    private fun optString(o: org.json.JSONObject, key: String): String = if (o.has(key) && !o.isNull(key)) o.optString(key, "") else ""

    private fun loadPersistentData(): Boolean = runCatching {
        val ordersJson = prefs.getString("orders", null)
        val customersJson = prefs.getString("customers", null)
        if (ordersJson == null && customersJson == null) return@runCatching false
        orderState.clear(); customerState.clear()
        ordersJson?.let { raw ->
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tagsArr = o.optJSONArray("tags") ?: org.json.JSONArray()
                val tags = buildList { for (j in 0 until tagsArr.length()) add(tagsArr.optString(j)) }
                orderState.add(Order(
                    code=optString(o,"code"), customer=optString(o,"customer"), phone=optString(o,"phone"),
                    address=optString(o,"address"), item=optString(o,"item"), amount=optString(o,"amount"), tags=tags,
                    shop=optString(o,"shop"), status=optString(o,"status").ifBlank { "Chưa giao" },
                    latitude=optString(o,"latitude"), longitude=optString(o,"longitude")
                ))
            }
        }
        customersJson?.let { raw ->
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val aliasesArr=o.optJSONArray("aliases") ?: org.json.JSONArray()
                val aliases=buildList { for(j in 0 until aliasesArr.length()) add(aliasesArr.optString(j)) }
                val phonesArr=o.optJSONArray("extraPhones") ?: org.json.JSONArray()
                val phones=buildList {
                    for(j in 0 until phonesArr.length()) {
                        val p=phonesArr.getJSONObject(j)
                        add(CustomerPhone(optString(p,"number"),optString(p,"action").ifBlank{"Gọi"},p.optBoolean("canCall",true),p.optBoolean("canZalo",true),p.optBoolean("canSms",true)))
                    }
                }
                val addrArr=o.optJSONArray("extraAddresses") ?: org.json.JSONArray()
                val addresses=buildList {
                    for(j in 0 until addrArr.length()) {
                        val a=addrArr.getJSONObject(j)
                        add(CustomerAddress(optString(a,"address"),optString(a,"latitude"),optString(a,"longitude"),a.optBoolean("isPrimary",false)))
                    }
                }
                customerState.add(Customer(
                    id=o.optLong("id",0L), name=optString(o,"name"), phone=optString(o,"phone"), address=optString(o,"address"),
                    latitude=optString(o,"latitude"), longitude=optString(o,"longitude"), initials=optString(o,"initials"), aliases=aliases,
                    extraPhones=phones, extraAddresses=addresses, note=optString(o,"note"),
                    primaryCanCall=o.optBoolean("primaryCanCall",true), primaryCanZalo=o.optBoolean("primaryCanZalo",true),
                    primaryCanSms=o.optBoolean("primaryCanSms",true), photoUri=optString(o,"photoUri")
                ))
            }
        }
        true
    }.getOrElse { false }

    private fun savePersistentData() {
        val ordersArr=org.json.JSONArray()
        orderState.forEach { o ->
            ordersArr.put(org.json.JSONObject().apply {
                put("code",o.code); put("customer",o.customer); put("phone",o.phone); put("address",o.address); put("item",o.item)
                put("amount",o.amount); put("tags",org.json.JSONArray(o.tags)); put("shop",o.shop); put("status",o.status)
                put("latitude",o.latitude); put("longitude",o.longitude)
            })
        }
        val customersArr=org.json.JSONArray()
        customerState.forEach { c ->
            customersArr.put(org.json.JSONObject().apply {
                put("id",c.id); put("name",c.name); put("phone",c.phone); put("address",c.address); put("latitude",c.latitude); put("longitude",c.longitude)
                put("initials",c.initials); put("aliases",org.json.JSONArray(c.aliases)); put("note",c.note)
                put("primaryCanCall",c.primaryCanCall); put("primaryCanZalo",c.primaryCanZalo); put("primaryCanSms",c.primaryCanSms); put("photoUri",c.photoUri)
                put("extraPhones",org.json.JSONArray().apply { c.extraPhones.forEach { p -> put(org.json.JSONObject().apply { put("number",p.number); put("action",p.action); put("canCall",p.canCall); put("canZalo",p.canZalo); put("canSms",p.canSms) }) } })
                put("extraAddresses",org.json.JSONArray().apply { c.extraAddresses.forEach { a -> put(org.json.JSONObject().apply { put("address",a.address); put("latitude",a.latitude); put("longitude",a.longitude); put("isPrimary",a.isPrimary) }) } })
            })
        }
        prefs.edit().putString("orders",ordersArr.toString()).putString("customers",customersArr.toString()).apply()
    }

    fun findCustomer(id: Long): Customer? = customerState.firstOrNull { it.id == id }

    private fun normalizeCustomerPhone(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
            digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
            else -> digits
        }
    }

    fun findCustomerByPhone(phone: String): Customer? {
        val wanted = normalizeCustomerPhone(phone)
        if (wanted.isBlank()) return null
        return customerState.firstOrNull { customer ->
            normalizeCustomerPhone(customer.phone) == wanted || customer.extraPhones.any { normalizeCustomerPhone(it.number) == wanted }
        }
    }

    fun ensureCustomerFromImportedOrder(name: String, phone: String, address: String): Customer? {
        val normalized = normalizeCustomerPhone(phone)
        if (normalized.isBlank()) return null
        findCustomerByPhone(phone)?.let { return it }
        val cleanName = name.trim().ifBlank { phone.trim() }
        val newId = (customerState.maxOfOrNull { it.id } ?: 0L) + 1L
        val created = Customer(id=newId,name=cleanName,phone=phone.trim(),address=address.trim(),initials=createInitials(cleanName))
        customerState.add(created); savePersistentData(); return created
    }

    fun addCustomer(customer: Customer): Long {
        val newId=(customerState.maxOfOrNull { it.id } ?: 0L)+1L
        customerState.add(customer.copy(id=newId)); savePersistentData(); return newId
    }

    fun updateCustomer(updatedCustomer: Customer) {
        val index=customerState.indexOfFirst { it.id==updatedCustomer.id }
        if(index>=0){ customerState[index]=updatedCustomer; savePersistentData() }
    }

    fun deleteCustomer(id: Long) { if(customerState.removeAll { it.id==id }) savePersistentData() }

    fun updateOrder(updated: Order) {
        val i=orderState.indexOfFirst { it.code==updated.code }
        if(i>=0){ orderState[i]=updated; savePersistentData() }
    }

    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }

    fun importVtmanRecords(records: List<com.example.giaohangpro.vtman.VtmanOrderRecord>) {
        var changed=false
        records.forEach { r ->
            val order=Order(code=r.waybill,customer=r.customer,phone=r.phone,address=r.address,item=r.goods,amount=r.cod,
                tags=r.service.split(',', ' ').map(String::trim).filter(String::isNotBlank),shop=r.shop,status=r.status)
            val index=orderState.indexOfFirst { it.code==r.waybill }
            if(index>=0) orderState[index]=order else orderState.add(order)
            ensureCustomerFromImportedOrder(r.customer,r.phone,r.address)
            changed=true
        }
        if(changed) savePersistentData()
    }
}
'''
    s=head+vm_marker+persistent_vm.split(vm_marker,1)[1]

p.write_text(s)
