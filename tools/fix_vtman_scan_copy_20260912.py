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

# Remember the order that opened customer detail so Back can return to the same list position.
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

# Scroll the order list back to the exact order after returning from customer detail.
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

# Anchor order bubbles by their bottom tip instead of the bitmap center.
s=s.replace('''                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(markerData.point.latitude, markerData.point.longitude))
                        .icon(numberIcon)
                        .title("Đơn #${markerData.number} • ${order.code}")
                        .snippet(if (markerData.hasRealCoordinate) order.address else "Chưa có tọa độ giao hàng")
                )''','''                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(markerData.point.latitude, markerData.point.longitude))
                        .icon(numberIcon)
                        .title("Đơn #${markerData.number} • ${order.code}")
                        .snippet(if (markerData.hasRealCoordinate) order.address else "Chưa có tọa độ giao hàng")
                ).setAnchor(0.5f, 1.0f)''',1)

# On entering the map tab, focus the camera once on the user's current GPS position.
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

p.write_text(s)
