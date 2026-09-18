from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
s=s.replace('enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM }','enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS }')
s=s.replace('AppScreen.MAIN -> Scaffold(\n            bottomBar = { BottomTabs(selected = tab, onSelected = { tab = it }) }','AppScreen.MAIN -> Scaffold(\n            topBar = { TopHeader(onSettingsClick = { screen = AppScreen.SETTINGS }) },\n            bottomBar = { BottomTabs(selected = tab, onSelected = { tab = it }) }')
needle='''        AppScreen.CUSTOMER_FORM -> CustomerFormScreen(
            customer = if (formIsNew) null else selectedCustomer,
            onBack = { screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL },
            onSave = { edited ->
                selectedCustomerId = if (formIsNew) vm.addCustomer(edited) else { vm.updateCustomer(edited); edited.id }
                screen = AppScreen.CUSTOMER_DETAIL
            }
        )
'''
repl=needle+'''        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN })
'''
s=s.replace(needle,repl)
s=s.replace('fun TopHeader() {','fun TopHeader(onSettingsClick: () -> Unit = {}) {')
s=s.replace('''        Icon(
            Icons.Default.Settings,
            contentDescription = "Cài đặt",
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )''','''        IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color.White, modifier = Modifier.size(19.dp))
        }''',1)
insert='''

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("CÀI ĐẶT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingsSection("DỮ LIỆU & ĐỒNG BỘ") {
                SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Sao lưu khách hàng hoặc quản lý đơn hàng") { Toast.makeText(context,"Import / Export dữ liệu",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ, lấy SĐT và xuất CSV từ VTMan") { Toast.makeText(context,"VTMan Export",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { Toast.makeText(context,"Tuyến giao hàng",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN") { Toast.makeText(context,"Bảng kê tiền",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp, top = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 48.dp else 56.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(OrangeLight), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = TextGray, maxLines = 2, lineHeight = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = Border) }
'''
marker='\nprivate val DEFAULT_MAP_POINT ='
s=s.replace(marker,insert+marker)
p.write_text(s)
