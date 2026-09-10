from pathlib import Path
import re

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

def replace_once(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'Missing anchor: {label}')
    s = s.replace(old, new, 1)
    print('updated', label)

def replace_section(start, end, body, label):
    global s
    a = s.find(start)
    if a < 0:
        raise SystemExit(f'Missing start: {label}')
    b = s.find(end, a)
    if b < 0:
        raise SystemExit(f'Missing end: {label}')
    s = s[:a] + body.rstrip() + '\n\n' + s[b:]
    print('replaced', label)

# Brand green used for COD/amounts.
if 'private val MoneyGreen' not in s:
    replace_once('private val CardWhite = Color.White', 'private val CardWhite = Color.White\nprivate val MoneyGreen = Color(0xFF168A45)', 'money green')

# Rich phone/address model while keeping old constructors compatible.
old_phone = '''data class CustomerPhone(\n    val number: String, // Số điện thoại cần lưu.\n    val action: String = "Gọi" // Nhãn thao tác mặc định của số điện thoại.\n)'''
new_phone = '''data class CustomerPhone(\n    val number: String,\n    val action: String = "Gọi",\n    val canCall: Boolean = true,\n    val canZalo: Boolean = true,\n    val canSms: Boolean = true\n)'''
replace_once(old_phone, new_phone, 'CustomerPhone model')

old_addr = '''data class CustomerAddress(\n    val address: String, // Chuỗi địa chỉ người dùng nhập.\n    val latitude: String = "", // Vĩ độ dạng text để dễ demo/chỉnh sửa.\n    val longitude: String = "" // Kinh độ dạng text để dễ demo/chỉnh sửa.\n)'''
new_addr = '''data class CustomerAddress(\n    val address: String,\n    val latitude: String = "",\n    val longitude: String = "",\n    val isPrimary: Boolean = false\n)'''
replace_once(old_addr, new_addr, 'CustomerAddress model')

# Add actions for the primary phone without breaking existing sample data.
replace_once('    val note: String = "" // Ghi chú về khách hàng.\n)', '    val note: String = "", // Ghi chú về khách hàng.\n    val primaryCanCall: Boolean = true,\n    val primaryCanZalo: Boolean = true,\n    val primaryCanSms: Boolean = true\n)', 'Customer primary phone actions')

# Smooth main tab transitions.
app_body = r'''@Composable
fun GiaoHangApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.MAP) }
    var screen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var formIsNew by remember { mutableStateOf(false) }
    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)

    when (screen) {
        AppScreen.MAIN -> Scaffold(
            bottomBar = { BottomTabs(selected = tab, onSelected = { tab = it }) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Background)) {
                androidx.compose.animation.Crossfade(
                    targetState = tab,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 170),
                    label = "main-tabs"
                ) { activeTab ->
                    when (activeTab) {
                        Tab.MAP -> MapScreen(vm.orders, vm.customers)
                        Tab.ORDERS -> OrderListScreen(vm.orders)
                        Tab.CUSTOMERS -> CustomerListScreen(
                            customers = vm.customers,
                            onCustomerClick = {
                                selectedCustomerId = it.id
                                screen = AppScreen.CUSTOMER_DETAIL
                            },
                            onAddCustomer = {
                                selectedCustomerId = null
                                formIsNew = true
                                screen = AppScreen.CUSTOMER_FORM
                            }
                        )
                    }
                }
            }
        }
        AppScreen.CUSTOMER_DETAIL -> {
            val c = selectedCustomer
            if (c == null) screen = AppScreen.MAIN else CustomerDetailScreen(
                customer = c,
                onBack = { screen = AppScreen.MAIN },
                onEdit = { formIsNew = false; screen = AppScreen.CUSTOMER_FORM },
                onDelete = { vm.deleteCustomer(c.id); selectedCustomerId = null; screen = AppScreen.MAIN }
            )
        }
        AppScreen.CUSTOMER_FORM -> CustomerFormScreen(
            customer = if (formIsNew) null else selectedCustomer,
            onBack = { screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL },
            onSave = { edited ->
                selectedCustomerId = if (formIsNew) vm.addCustomer(edited) else { vm.updateCustomer(edited); edited.id }
                screen = AppScreen.CUSTOMER_DETAIL
            }
        )
    }
}'''
replace_section('@Composable\nfun GiaoHangApp', '// ================================================================\n// 4. HEADER', app_body, 'GiaoHangApp')

# Compact map order list and green amount.
s = s.replace('.height(68.dp)', '.height(62.dp)', 1)
s = s.replace('.padding(horizontal = 9.dp, vertical = 6.dp)', '.padding(horizontal = 4.dp, vertical = 3.dp)', 1)
s = s.replace('Spacer(Modifier.width(8.dp))\n            Box(\n                modifier = Modifier\n                    .size(24.dp)', 'Spacer(Modifier.width(4.dp))\n            Box(\n                modifier = Modifier\n                    .size(28.dp)', 1)
s = s.replace('modifier = Modifier.size(12.dp)\n                )\n            }\n            Spacer(Modifier.width(8.dp))', 'modifier = Modifier.size(16.dp)\n                )\n            }\n            Spacer(Modifier.width(4.dp))', 1)
s = s.replace('Text(order.code, color = Navy, fontSize = 17.sp', 'Text(order.code, color = Navy, fontSize = 15.sp', 1)
s = s.replace('Text(order.amount, color = OrangeDark, fontSize = 16.sp', 'Text(order.amount, color = MoneyGreen, fontSize = 16.sp', 1)

# Order details: tags TTxxx and amount smaller by two sizes; amount green.
s = s.replace('.padding(horizontal = 10.dp, vertical = 4.dp)\n    ) {\n        Text(text, color = Navy, fontSize = 12.sp', '.padding(horizontal = 7.dp, vertical = 3.dp)\n    ) {\n        Text(text, color = Navy, fontSize = 10.sp', 1)
s = s.replace('order.amount,\n                    color = OrangeDark,\n                    fontSize = 15.sp', 'order.amount,\n                    color = MoneyGreen,\n                    fontSize = 13.sp', 1)

# Slightly larger numbered circle everywhere it is used; this is especially visible in map rows.
num_start = s.find('@Composable\nfun NumberCircle')
if num_start >= 0:
    num_end = s.find('// ================================================================', num_start)
    chunk = s[num_start:num_end if num_end > 0 else len(s)]
    chunk2 = chunk.replace('.size(30.dp)', '.size(34.dp)', 1).replace('fontSize = 13.sp', 'fontSize = 14.sp', 1)
    s = s[:num_start] + chunk2 + s[num_end if num_end > 0 else len(s):]

section8 = r'''// ================================================================
// 8. CHI TIẾT KHÁCH HÀNG
// ================================================================
@Composable
fun CustomerDetailScreen(customer: Customer, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun phoneFor(kind: String): String? {
        val primaryOk = when (kind) {
            "call" -> customer.primaryCanCall
            "zalo" -> customer.primaryCanZalo
            else -> customer.primaryCanSms
        }
        if (primaryOk && customer.phone.isNotBlank()) return customer.phone
        return customer.extraPhones.firstOrNull {
            when (kind) { "call" -> it.canCall; "zalo" -> it.canZalo; else -> it.canSms }
        }?.number
    }
    fun launchPhone(kind: String) {
        val raw = phoneFor(kind) ?: return
        val number = raw.filter(Char::isDigit)
        val uri = when (kind) {
            "call" -> android.net.Uri.parse("tel:$number")
            "zalo" -> android.net.Uri.parse("https://zalo.me/$number")
            else -> android.net.Uri.parse("smsto:$number")
        }
        val action = if (kind == "call") android.content.Intent.ACTION_DIAL else android.content.Intent.ACTION_VIEW
        runCatching { context.startActivity(android.content.Intent(action, uri)) }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("CHI TIẾT KHÁCH HÀNG", onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CustomerDetailContent(customer, Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp))
            CustomerSideActions(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 3.dp, bottom = 3.dp),
                onCall = { launchPhone("call") },
                onZalo = { launchPhone("zalo") },
                onSms = { launchPhone("sms") },
                onNavigate = {
                    pointFromStrings(customer.latitude, customer.longitude)?.let { openGoogleNavigation(context, it) }
                },
                onEdit = onEdit,
                onDelete = { showDeleteDialog = true }
            )
        }
    }

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Xóa khách hàng") },
        text = { Text("Bạn có chắc muốn xóa ${customer.name} không?") },
        confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Xóa", color = Color(0xFFE21B1B)) } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") } }
    )
}

@Composable
private fun CustomerPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(3.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CustomerSideActions(
    modifier: Modifier = Modifier,
    onCall: () -> Unit, onZalo: () -> Unit, onSms: () -> Unit, onNavigate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        DetailActionButton("Gọi", Icons.Default.Call, Orange, onCall)
        DetailActionButton("Zalo", Icons.Default.Chat, Orange, onZalo)
        DetailActionButton("SMS", Icons.Default.ChatBubbleOutline, Orange, onSms)
        DetailActionButton("Đường", Icons.Default.Navigation, Orange, onNavigate)
        DetailActionButton("Sửa", Icons.Default.Edit, Orange, onEdit)
        DetailActionButton("Xóa", Icons.Default.DeleteOutline, Color(0xFFE21B1B), onDelete)
    }
}

@Composable
private fun DetailActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 43.dp, height = 36.dp).clickable { onClick() },
        shape = RoundedCornerShape(9.dp), colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(2.dp))
            Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CustomerDetailContent(customer: Customer, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Card(
            Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(14.dp),
            CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, "Cổng nhà khách", tint = Orange, modifier = Modifier.size(72.dp))
                    Text("Hình cổng nhà khách", color = TextGray, fontSize = 13.sp)
                }
            }
        }
        DetailInfoCard(Icons.Default.Person, customer.name)
        DetailInfoCard(Icons.Default.Phone, customer.phone)
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(customer.address, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (customer.latitude.isBlank() || customer.longitude.isBlank()) "Chưa có tọa độ" else "${customer.latitude}, ${customer.longitude}", color = TextGray, fontSize = 11.sp)
                }
            }
        }
        if (customer.note.isNotBlank()) Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) { Text(customer.note, Modifier.padding(7.dp), color = Navy, fontSize = 13.sp) }
    }
}

@Composable
private fun DetailInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(7.dp))
            Text(value, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}'''

section9 = r'''// ================================================================
// 9. THÊM / SỬA KHÁCH HÀNG
// ================================================================
private data class PhoneDraft(val number: String, val canCall: Boolean, val canZalo: Boolean, val canSms: Boolean)
private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean)

@Composable
fun CustomerFormScreen(customer: Customer?, onBack: () -> Unit, onSave: (Customer) -> Unit) {
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var note by remember(customer?.id) { mutableStateOf(customer?.note.orEmpty()) }
    var expandedPhone by remember { mutableStateOf<Int?>(null) }
    var pickAddressIndex by remember { mutableStateOf<Int?>(null) }
    var validation by remember { mutableStateOf(false) }

    val phones = remember(customer?.id) {
        mutableStateListOf<PhoneDraft>().apply {
            if (customer == null) add(PhoneDraft("", true, true, true)) else {
                add(PhoneDraft(customer.phone, customer.primaryCanCall, customer.primaryCanZalo, customer.primaryCanSms))
                addAll(customer.extraPhones.map { PhoneDraft(it.number, it.canCall, it.canZalo, it.canSms) })
            }
        }
    }
    val addresses = remember(customer?.id) {
        mutableStateListOf<AddressDraft>().apply {
            if (customer == null) add(AddressDraft("", "", "", true)) else {
                add(AddressDraft(customer.address, customer.latitude, customer.longitude, true))
                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false) })
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader(if (customer == null) "THÊM KHÁCH HÀNG" else "SỬA KHÁCH HÀNG", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CustomerPhotoCard()
            CompactInput("Tên khách hàng", name, { name = it }, "Nhập tên khách hàng")

            Text("SỐ ĐIỆN THOẠI", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            phones.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = item.number,
                                onValueChange = { phones[index] = item.copy(number = it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text(if (index == 0) "SĐT chính" else "SĐT phụ", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                            IconButton(onClick = { expandedPhone = if (expandedPhone == index) null else index }, modifier = Modifier.size(38.dp)) {
                                Icon(if (expandedPhone == index) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Chọn chức năng")
                            }
                            SmallPlusMinus(plus = true) { phones.add(index + 1, PhoneDraft("", false, false, false)) }
                            if (phones.size > 1) SmallPlusMinus(plus = false) { phones.removeAt(index); expandedPhone = null }
                        }
                        if (expandedPhone == index) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ActionCheck("Gọi", item.canCall) { phones[index] = phones[index].copy(canCall = it) }
                            ActionCheck("Zalo", item.canZalo) { phones[index] = phones[index].copy(canZalo = it) }
                            ActionCheck("SMS", item.canSms) { phones[index] = phones[index].copy(canSms = it) }
                        }
                    }
                }
            }

            Text("ĐỊA CHỈ + ĐỊNH VỊ", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            addresses.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.isPrimary,
                                onCheckedChange = { checked -> if (checked) addresses.indices.forEach { i -> addresses[i] = addresses[i].copy(isPrimary = i == index) } },
                                modifier = Modifier.size(34.dp)
                            )
                            Text("Chính", fontSize = 11.sp)
                            Spacer(Modifier.width(3.dp))
                            OutlinedTextField(
                                value = item.address,
                                onValueChange = { addresses[index] = item.copy(address = it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Địa chỉ", fontSize = 12.sp) }
                            )
                            SmallPlusMinus(plus = true) { addresses.add(index + 1, AddressDraft("", "", "", false)) }
                            if (addresses.size > 1) SmallPlusMinus(plus = false) {
                                val wasPrimary = addresses[index].isPrimary
                                addresses.removeAt(index)
                                if (wasPrimary && addresses.isNotEmpty()) addresses[0] = addresses[0].copy(isPrimary = true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (item.latitude.isBlank() || item.longitude.isBlank()) "Chưa chọn tọa độ" else "${item.latitude}, ${item.longitude}",
                                color = TextGray, fontSize = 11.sp, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { pickAddressIndex = index }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(2.dp)); Text("Chọn trên bản đồ", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            CompactInput("Ghi chú", note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
            if (validation) Text("Cần nhập tên, ít nhất 1 SĐT và 1 địa chỉ.", color = Color(0xFFE21B1B), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = {
                    val validPhones = phones.filter { it.number.isNotBlank() }
                    val validAddresses = addresses.filter { it.address.isNotBlank() }
                    if (name.isBlank() || validPhones.isEmpty() || validAddresses.isEmpty()) validation = true else {
                        val primaryAddress = validAddresses.firstOrNull { it.isPrimary } ?: validAddresses.first()
                        val primaryPhone = validPhones.first()
                        onSave(Customer(
                            id = customer?.id ?: 0L,
                            name = name.trim(),
                            phone = primaryPhone.number.trim(),
                            address = primaryAddress.address.trim(),
                            latitude = primaryAddress.latitude.trim(),
                            longitude = primaryAddress.longitude.trim(),
                            initials = createInitials(name),
                            aliases = customer?.aliases ?: emptyList(),
                            extraPhones = validPhones.drop(1).map { CustomerPhone(it.number.trim(), if (it.canCall) "Gọi" else if (it.canZalo) "Zalo" else "SMS", it.canCall, it.canZalo, it.canSms) },
                            extraAddresses = validAddresses.filter { it !== primaryAddress }.map { CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false) },
                            note = note.trim(),
                            primaryCanCall = primaryPhone.canCall,
                            primaryCanZalo = primaryPhone.canZalo,
                            primaryCanSms = primaryPhone.canSms
                        ))
                    }
                },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("LƯU", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("Hủy") }
        }
    }

    pickAddressIndex?.let { index ->
        CustomerCoordinateMapPicker(
            initialPoint = pointFromStrings(addresses[index].latitude, addresses[index].longitude),
            focusUserLocation = true,
            onDismiss = { pickAddressIndex = null },
            onSavePoint = { point ->
                addresses[index] = addresses[index].copy(
                    latitude = "%.6f".format(java.util.Locale.US, point.latitude),
                    longitude = "%.6f".format(java.util.Locale.US, point.longitude)
                )
                pickAddressIndex = null
            }
        )
    }
}

@Composable
private fun ActionCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChecked(!checked) }) {
        Checkbox(checked, onChecked, modifier = Modifier.size(32.dp)); Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun SmallPlusMinus(plus: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(if (plus) Icons.Default.AddCircle else Icons.Default.RemoveCircle, if (plus) "Thêm" else "Xóa", tint = if (plus) Orange else Color(0xFFE21B1B), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CompactInput(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true, minLines: Int = 1) {
    Column {
        Text(label, color = TextGray, fontSize = 11.sp)
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), placeholder = { Text(placeholder, fontSize = 12.sp) }, singleLine = singleLine, minLines = minLines, shape = RoundedCornerShape(12.dp))
    }
}

@Composable
private fun CustomerPhotoCard() {
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Storefront, "Ảnh khách hàng", tint = Orange, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(4.dp)); Text("Hình cổng nhà khách", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomerCoordinateMapPicker(
    initialPoint: MapPoint?,
    focusUserLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val driverLocation by rememberDriverLocation()
    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }
    val start = if (focusUserLocation) (driverLocation ?: initialPoint ?: DEFAULT_MAP_POINT) else (initialPoint ?: DEFAULT_MAP_POINT)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),
            shape = RoundedCornerShape(14.dp), color = Background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Đóng") }
                    Text("CHỌN TỌA ĐỘ", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    driverLocation?.let { TextButton(onClick = { selected = it }) { Text("Vị trí tôi", fontSize = 11.sp) } }
                }
                CoordinatePickerMap(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    initialPoint = start,
                    driverLocation = driverLocation,
                    selectedPoint = selected,
                    onPointSelected = { selected = it }
                )
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onSavePoint(selected) }, modifier = Modifier.height(40.dp)) { Text("LƯU TỌA ĐỘ", fontSize = 12.sp) }
                }
            }
        }
    }
}'''

replace_section('// ================================================================\n// 8. CHI TIẾT KHÁCH HÀNG', '// ================================================================\n// 9. THÊM / SỬA KHÁCH HÀNG', section8, 'customer detail section')
replace_section('// ================================================================\n// 9. THÊM / SỬA KHÁCH HÀNG', '// ================================================================\n// 10. Ô TÌM KIẾM + SỐ THỨ TỰ', section9, 'customer form section')

p.write_text(s)
print('UI patch complete')
