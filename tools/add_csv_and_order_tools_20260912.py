from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

s=s.replace('''Tab.ORDERS -> OrderListScreen(\n                            orders = vm.orders,''','''Tab.ORDERS -> OrderListScreen(\n                            vm = vm,''',1)

start=s.index('@Composable\nprivate fun VtmanExportScreen(')
end=s.index('\n@Composable\nprivate fun SettingsSection', start)
vt=r'''@Composable
private fun VtmanExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var waybills by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(com.example.giaohangpro.vtman.VtmanQueueController.snapshot()) }
    var importedCount by remember { mutableIntStateOf(0) }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val codes = text.lineSequence().map { it.trim().removePrefix("\uFEFF") }
                .filter { it.isNotBlank() }
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter { it.isNotBlank() }.toList()
            waybills = codes.joinToString("\n")
            com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            Toast.makeText(context, "Đã nạp ${codes.size} MVĐ từ CSV", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không đọc được file CSV", Toast.LENGTH_LONG).show() }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val records = com.example.giaohangpro.vtman.VtmanQueueController.records()
            val csv = buildString {
                append("MVĐ,Shop,SĐT,Tên khách,COD,Địa chỉ,Hàng hóa,Trạng thái,Dịch vụ\n")
                records.forEach { r -> append(listOf(r.waybill,r.shop,r.phone,r.customer,r.cod,r.address,r.goods,r.status,r.service).joinToString(",") { csvCell(it) }).append('\n') }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            Toast.makeText(context, "Đã xuất ${records.size} đơn", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không xuất được CSV", Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val records = com.example.giaohangpro.vtman.VtmanQueueController.records()
            if (records.size != importedCount) { vm.importVtmanRecords(records); importedCount = records.size }
            kotlinx.coroutines.delay(400)
        }
    }
    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().height(40.dp).background(Orange).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White) }
            Text("VTMan Export", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(6.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Danh sách mã vận đơn", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = waybills, onValueChange = { waybills = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp), placeholder = { Text("Mỗi dòng 1 mã vận đơn", fontSize = 12.sp) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { csvImportLauncher.launch("text/*") }, modifier = Modifier.weight(1f).height(42.dp)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("NẠP MVĐ", fontSize=11.sp) }
                OutlinedButton(onClick = {
                    val list = waybills.lines().flatMap { it.split(',', ';', ' ', '\t') }.map(String::trim).filter(String::isNotBlank)
                    com.example.giaohangpro.vtman.VtmanQueueController.load(list); snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                }, modifier = Modifier.weight(1f).height(42.dp)) { Text("NẠP TEXTBOX", fontSize=11.sp) }
            }
            OutlinedButton(onClick = { csvExportLauncher.launch("vtman_orders.csv") }, modifier = Modifier.fillMaxWidth().height(40.dp)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(4.dp)); Text("XUẤT CSV THÔNG TIN ĐƠN", fontSize=11.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    if (!android.provider.Settings.canDrawOverlays(context)) context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
                    else { context.startService(android.content.Intent(context, com.example.giaohangpro.vtman.VtmanOverlayService::class.java)); Toast.makeText(context, "Mở VTMan > Gạch phát offline, rồi bấm Chạy trên popup", Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.weight(1f).height(42.dp)) { Text("BẬT POPUP", fontSize = 11.sp) }
                OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.weight(1f).height(42.dp)) { Text("TRỢ NĂNG", fontSize = 11.sp) }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Tiến độ: ${snapshot.processed}/${snapshot.total} • Lấy được: ${snapshot.written} • Bỏ qua: ${snapshot.skipped}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(snapshot.status, fontSize = 12.sp, color = if (snapshot.error.isBlank()) TextGray else Color(0xFFE21B1B))
                if (snapshot.currentWaybill.isNotBlank()) Text("Đang xử lý: ${snapshot.currentWaybill}", fontSize = 12.sp, color = OrangeDark)
            } }
            Text("CSV đầu vào: mỗi dòng 1 MVĐ. CSV đầu ra giữ lại toàn bộ thông tin để có thể nạp/đối chiếu lại khi cần.", fontSize = 11.sp, color = TextGray)
        }
    }
}
private fun csvCell(v:String):String = "\"" + v.replace("\"", "\"\"") + "\""
'''
s=s[:start]+vt+s[end:]

start=s.index('@Composable\nfun OrderListScreen(')
end=s.index('\n@Composable\nfun OrderCard', start)
orders=r'''@Composable
fun OrderListScreen(vm: MainViewModel, onCustomerClick: (Order) -> Unit) {
    val context = LocalContext.current
    val orders = vm.orders
    var keyword by remember { mutableStateOf("") }
    var showTools by remember { mutableStateOf(false) }
    var editPicker by remember { mutableStateOf(false) }
    var editOrder by remember { mutableStateOf<Order?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword = it } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv=buildString { append("MVĐ,Shop,SĐT,Tên khách,COD,Địa chỉ,Hàng hóa,Trạng thái,Dịch vụ\n"); orders.forEach { o -> append(listOf(o.code,o.shop,o.phone,o.customer,o.amount,o.address,o.item,o.status,o.tags.joinToString(" ")).joinToString(",") { csvCell(it) }).append('\n') } }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context,"Đã xuất ${orders.size} đơn",Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(context,"Không xuất được danh sách",Toast.LENGTH_LONG).show() }
    }
    val filteredOrders = remember(orders, keyword) { val q=keyword.trim(); if(q.isBlank()) orders else orders.filter { it.code.contains(q,true)||it.customer.contains(q,true)||it.phone.contains(q,true)||it.address.contains(q,true) } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Spacer(Modifier.height(6.dp))
            SearchBox(keyword,{keyword=it},{keyword=""}) { scanLauncher.launch(ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung"); setBeepEnabled(false); setCaptureActivity(PortraitCaptureActivity::class.java); setOrientationLocked(true); setBarcodeImageEnabled(false) }) }
            Spacer(Modifier.height(6.dp)); Text("Tổng số: ${filteredOrders.size} đơn", color=Navy,fontSize=20.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp), contentPadding=PaddingValues(bottom=62.dp)) { itemsIndexed(filteredOrders) { index, order -> OrderCard(index+1,order,onCustomerClick) } }
        }
        FloatingActionButton(onClick={showTools=true}, modifier=Modifier.align(Alignment.BottomStart).padding(10.dp).size(44.dp), containerColor=Orange) { Icon(Icons.Default.Edit,"Công cụ đơn",tint=Color.White) }
        DropdownMenu(expanded=showTools,onDismissRequest={showTools=false}, modifier=Modifier.align(Alignment.BottomStart)) {
            DropdownMenuItem(text={Text("Xuất danh sách đơn")},leadingIcon={Icon(Icons.Default.FileDownload,null)},onClick={showTools=false;exportLauncher.launch("giaohangpro_orders.csv")})
            DropdownMenuItem(text={Text("Sửa đơn hàng")},leadingIcon={Icon(Icons.Default.Edit,null)},onClick={showTools=false;editPicker=true})
            DropdownMenuItem(text={Text("Xóa đơn hàng")},leadingIcon={Icon(Icons.Default.Delete,null)},onClick={showTools=false;deleteMode=true})
        }
    }
    if(editPicker) AlertDialog(onDismissRequest={editPicker=false},title={Text("Chọn đơn cần sửa")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){filteredOrders.forEach { o -> Row(Modifier.fillMaxWidth().clickable{editOrder=o;editPicker=false}.padding(10.dp)){Text(o.code,Modifier.weight(1f));Text(o.customer,fontSize=11.sp,color=TextGray)} }}},confirmButton={},dismissButton={TextButton(onClick={editPicker=false}){Text("ĐÓNG")}})
    editOrder?.let { original ->
        var customer by remember(original.code){mutableStateOf(original.customer)}; var phone by remember(original.code){mutableStateOf(original.phone)}; var address by remember(original.code){mutableStateOf(original.address)}; var amount by remember(original.code){mutableStateOf(original.amount)}
        AlertDialog(onDismissRequest={editOrder=null},title={Text("Sửa ${original.code}")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){OutlinedTextField(customer,{customer=it},label={Text("Tên khách")});OutlinedTextField(phone,{phone=it},label={Text("SĐT")});OutlinedTextField(address,{address=it},label={Text("Địa chỉ")});OutlinedTextField(amount,{amount=it},label={Text("COD")})}},confirmButton={TextButton(onClick={vm.updateOrder(original.copy(customer=customer,phone=phone,address=address,amount=amount));editOrder=null}){Text("LƯU")}},dismissButton={TextButton(onClick={editOrder=null}){Text("HỦY")}})
    }
    if(deleteMode) AlertDialog(onDismissRequest={deleteMode=false},title={Text("Xóa đơn hàng")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){filteredOrders.forEach { o -> Row(Modifier.fillMaxWidth().clickable{vm.deleteOrder(o.code);deleteMode=false}.padding(10.dp)){Text(o.code,Modifier.weight(1f));Icon(Icons.Default.Delete,null,tint=Color(0xFFE21B1B))} }}},confirmButton={},dismissButton={TextButton(onClick={deleteMode=false}){Text("ĐÓNG")}})
}
'''
s=s[:start]+orders+s[end:]
needle='''    fun importVtmanRecords(records: List<com.example.giaohangpro.vtman.VtmanOrderRecord>) {'''
if 'fun updateOrder(updated: Order)' not in s:
    s=s.replace(needle,'''    fun updateOrder(updated: Order) {\n        val i = orderState.indexOfFirst { it.code == updated.code }\n        if (i >= 0) orderState[i] = updated\n    }\n\n    fun deleteOrder(code: String) { orderState.removeAll { it.code == code } }\n\n'''+needle,1)
p.write_text(s)
