from pathlib import Path

main = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = main.read_text()

# Order keeps shop separately so the orderer's name can differ from the shop name.
s = s.replace('''    val tags: List<String>,
    val status: String = "Chưa giao",''','''    val tags: List<String>,
    val shop: String = "",
    val status: String = "Chưa giao",''',1)

s = s.replace('enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER }',
              'enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER, VTMAN_EXPORT }')

s = s.replace('''            AppScreen.MONEY_LEDGER -> screen = AppScreen.SETTINGS
            AppScreen.MAIN -> {''','''            AppScreen.MONEY_LEDGER -> screen = AppScreen.SETTINGS
            AppScreen.VTMAN_EXPORT -> screen = AppScreen.SETTINGS
            AppScreen.MAIN -> {''',1)

s = s.replace('AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })',
              'AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER }, onVtmanExport = { screen = AppScreen.VTMAN_EXPORT })',1)
s = s.replace('AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })',
              'AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })\n        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })',1)

s = s.replace('private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit) {',
              'private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit, onVtmanExport: () -> Unit) {',1)
s = s.replace('SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ, lấy SĐT và xuất CSV từ VTMan") { Toast.makeText(context,"VTMan Export",Toast.LENGTH_SHORT).show() }',
              'SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ và lấy thông tin đơn trực tiếp từ VTMan") { onVtmanExport() }',1)

# Show shop on the Store row when VTMan supplied it.
s = s.replace('OrderInfoRow(Icons.Default.Store, order.customer)', 'OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })',1)

# ViewModel orders become mutable and VTMan import is append/replace by waybill, while customer import is strictly insert-only by phone.
s = s.replace('''    // Đơn hàng hiện vẫn dùng dữ liệu mẫu; sau này có thể thay bằng Repository/API.
    val orders = sampleOrders
''','''    private val orderState = mutableStateListOf<Order>().apply { addAll(sampleOrders) }
    val orders: List<Order> get() = orderState
''',1)

needle = '''    // Xóa khách theo id.
    fun deleteCustomer(id: Long) {
        customerState.removeAll { it.id == id } // Loại bỏ toàn bộ phần tử trùng id.
    }
}'''
if needle in s:
    repl = '''    // Xóa khách theo id.
    fun deleteCustomer(id: Long) {
        customerState.removeAll { it.id == id }
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.startsWith("0084") -> "0" + digits.drop(4)
            digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
            else -> digits
        }
    }

    // Quy tắc bảo vệ dữ liệu khách: nếu SĐT đã tồn tại thì tuyệt đối không sửa bất kỳ trường nào.
    fun ensureCustomerFromImportedOrder(name: String, phone: String, address: String): Long? {
        val wanted = normalizePhone(phone)
        if (wanted.isBlank()) return null
        val existing = customerState.firstOrNull { c ->
            normalizePhone(c.phone) == wanted || c.extraPhones.any { normalizePhone(it.number) == wanted }
        }
        if (existing != null) return existing.id
        val newId = (customerState.maxOfOrNull { it.id } ?: 0L) + 1L
        customerState.add(Customer(id = newId, name = name.trim().ifBlank { phone }, phone = phone.trim(), address = address.trim(), initials = createInitials(name), latitude = "", longitude = ""))
        return newId
    }

    fun importVtmanRecords(records: List<com.example.giaohangpro.vtman.VtmanOrderRecord>) {
        records.forEach { r ->
            val order = Order(
                code = r.waybill,
                customer = r.customer,
                phone = r.phone,
                address = r.address,
                item = r.goods,
                amount = r.cod,
                tags = r.service.split(',', ' ').map(String::trim).filter(String::isNotBlank),
                shop = r.shop,
                status = r.status
            )
            val index = orderState.indexOfFirst { it.code == r.waybill }
            if (index >= 0) orderState[index] = order else orderState.add(order)
            ensureCustomerFromImportedOrder(r.customer, r.phone, r.address)
        }
    }
}'''
    s = s.replace(needle, repl, 1)

# Insert VTMan screen once before SettingsSection.
if 'private fun VtmanExportScreen(' not in s:
    marker = '\n@Composable\nprivate fun SettingsSection('
    screen = r'''

@Composable
private fun VtmanExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var waybills by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(com.example.giaohangpro.vtman.VtmanQueueController.snapshot()) }
    var importedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val records = com.example.giaohangpro.vtman.VtmanQueueController.records()
            if (records.size != importedCount) {
                vm.importVtmanRecords(records)
                importedCount = records.size
            }
            kotlinx.coroutines.delay(400)
        }
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Orange).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White) }
                Text("VTMan Export", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(6.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Dán danh sách mã vận đơn, mỗi mã một dòng", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = waybills,
                onValueChange = { waybills = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                placeholder = { Text("150327113189\n150406398811", fontSize = 12.sp) }
            )
            Button(onClick = {
                val list = waybills.lines().flatMap { line -> line.split(',', ';', ' ', '\t') }.map(String::trim).filter { it.isNotBlank() }
                com.example.giaohangpro.vtman.VtmanQueueController.load(list)
                snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            }, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("NẠP DANH SÁCH") }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    if (!android.provider.Settings.canDrawOverlays(context)) {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    } else {
                        context.startService(android.content.Intent(context, com.example.giaohangpro.vtman.VtmanOverlayService::class.java))
                        Toast.makeText(context, "Mở VTMan > Gạch phát offline, rồi bấm Chạy trên popup", Toast.LENGTH_LONG).show()
                    }
                }, modifier = Modifier.weight(1f).height(42.dp)) { Text("BẬT POPUP", fontSize = 11.sp) }
                OutlinedButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }, modifier = Modifier.weight(1f).height(42.dp)) { Text("TRỢ NĂNG", fontSize = 11.sp) }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Tiến độ: ${snapshot.processed}/${snapshot.total} • Lấy được: ${snapshot.written} • Bỏ qua: ${snapshot.skipped}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Text(snapshot.status, fontSize = 12.sp, color = if (snapshot.error.isBlank()) TextGray else Color(0xFFE21B1B))
                    if (snapshot.currentWaybill.isNotBlank()) Text("Đang xử lý: ${snapshot.currentWaybill}", fontSize = 12.sp, color = OrangeDark)
                }
            }
            Text("Dữ liệu lấy: MVĐ • Shop • SĐT • Tên khách • COD • Địa chỉ • Hàng hóa • Trạng thái • Dịch vụ. Khách cũ theo SĐT tuyệt đối không bị ghi đè.", fontSize = 11.sp, color = TextGray)
        }
    }
}
'''
    s = s.replace(marker, screen + marker, 1)

main.write_text(s)

# Register accessibility and overlay services.
manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text()
if 'SYSTEM_ALERT_WINDOW' not in m:
    m = m.replace('<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />', '<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />\n    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />')
if 'VtmanAccessibilityService' not in m:
    insert = '''\n        <service\n            android:name=".vtman.VtmanAccessibilityService"\n            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"\n            android:exported="true">\n            <intent-filter>\n                <action android:name="android.accessibilityservice.AccessibilityService" />\n            </intent-filter>\n            <meta-data\n                android:name="android.accessibilityservice"\n                android:resource="@xml/vtman_accessibility_service" />\n        </service>\n        <service android:name=".vtman.VtmanOverlayService" android:exported="false" />\n'''
    m = m.replace('        <activity android:name=".MainActivity" android:exported="false" />', '        <activity android:name=".MainActivity" android:exported="false" />' + insert)
manifest.write_text(m)

xml = Path('app/src/main/res/xml/vtman_accessibility_service.xml')
xml.parent.mkdir(parents=True, exist_ok=True)
xml.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"\n    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged"\n    android:accessibilityFeedbackType="feedbackGeneric"\n    android:notificationTimeout="120"\n    android:canRetrieveWindowContent="true"\n    android:canPerformGestures="true" />\n''')
