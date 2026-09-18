from pathlib import Path
import re

p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

start=s.index('@Composable\nfun CustomerFormScreen(')
end=s.index('@Composable\nprivate fun ActionCheck', start)

new=r'''@Composable
fun CustomerFormScreen(customer: Customer?, onBack: () -> Unit, onSave: (Customer) -> Unit) {
    val context = LocalContext.current
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var note by remember(customer?.id) { mutableStateOf(customer?.note.orEmpty()) }
    var photoUri by remember(customer?.id) { mutableStateOf(customer?.photoUri.orEmpty()) }
    val names = remember(customer?.id) { mutableStateListOf<String>().apply { add(customer?.name.orEmpty()); addAll(customer?.aliases.orEmpty()) } }
    var expandedPhone by remember { mutableStateOf<Int?>(null) }
    var pickAddressIndex by remember { mutableStateOf<Int?>(null) }
    var validation by remember { mutableStateOf(false) }
    var showFormPhotoMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        photoUri = uri.toString()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCameraUri?.let { photoUri = it.toString() }
        pendingCameraUri = null
    }

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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                CustomerPhotoCard(photoUri)
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp).size(38.dp).clickable { showFormPhotoMenu = true }, CircleShape, color = Orange) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, "Quản lý ảnh", tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
            }
            names.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { CompactInput(if (index == 0) "Tên khách hàng" else "Tên / biệt danh", value, { v -> names[index] = v; if (index == 0) name = v }, if (index == 0) "Nhập tên khách hàng" else "Nhập tên hoặc biệt danh") }
                    SmallPlusMinus(plus = true) { names.add(index + 1, "") }
                    if (names.size > 1 && index > 0) SmallPlusMinus(plus = false) { names.removeAt(index) }
                }
            }

            Text("SỐ ĐIỆN THOẠI", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            phones.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = item.number, onValueChange = { phones[index] = item.copy(number = it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(if (index == 0) "SĐT chính" else "SĐT phụ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            IconButton(onClick = { expandedPhone = if (expandedPhone == index) null else index }, modifier = Modifier.size(30.dp)) { Icon(if (expandedPhone == index) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Chọn chức năng") }
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
                            Checkbox(checked = item.isPrimary, onCheckedChange = { checked -> if (checked) addresses.indices.forEach { i -> addresses[i] = addresses[i].copy(isPrimary = i == index) } }, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(3.dp))
                            OutlinedTextField(value = item.address, onValueChange = { addresses[index] = item.copy(address = it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Địa chỉ", fontSize = 12.sp) })
                            SmallPlusMinus(plus = true) { addresses.add(index + 1, AddressDraft("", "", "", false)) }
                            if (addresses.size > 1) SmallPlusMinus(plus = false) {
                                val wasPrimary = addresses[index].isPrimary; addresses.removeAt(index)
                                if (wasPrimary && addresses.isNotEmpty()) addresses[0] = addresses[0].copy(isPrimary = true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fun setCoord(raw: String, latitudeField: Boolean) {
                                val pair = Regex("""([-+]?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)""").find(raw.trim())
                                if (pair != null) {
                                    var a = pair.groupValues[1]; var b = pair.groupValues[2]
                                    val av=a.toDoubleOrNull(); val bv=b.toDoubleOrNull()
                                    if (av != null && bv != null && kotlin.math.abs(av) > 90 && kotlin.math.abs(bv) <= 90) { val t=a; a=b; b=t }
                                    addresses[index] = addresses[index].copy(latitude = a, longitude = b)
                                } else if (latitudeField) addresses[index] = addresses[index].copy(latitude = raw.replace(',', '.'))
                                else addresses[index] = addresses[index].copy(longitude = raw.replace(',', '.'))
                            }
                            OutlinedTextField(item.latitude, { setCoord(it, true) }, Modifier.weight(1f), singleLine = true, label = { Text("Vĩ độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(item.longitude, { setCoord(it, false) }, Modifier.weight(1f), singleLine = true, label = { Text("Kinh độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            TextButton(onClick = { pickAddressIndex = index }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(2.dp)); Text("Chọn trên bản đồ", fontSize = 11.sp) }
                        }
                    }
                }
            }

            CompactInput("Ghi chú", note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
            if (validation) Text("Cần nhập tên, ít nhất 1 SĐT và 1 địa chỉ.", color = Color(0xFFE21B1B), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                val validPhones = phones.filter { it.number.isNotBlank() }; val validAddresses = addresses.filter { it.address.isNotBlank() }
                if (name.isBlank() || validPhones.isEmpty() || validAddresses.isEmpty()) validation = true else {
                    val primaryAddress = validAddresses.firstOrNull { it.isPrimary } ?: validAddresses.first(); val primaryPhone = validPhones.first()
                    onSave(Customer(id = customer?.id ?: 0L, name = name.trim(), phone = primaryPhone.number.trim(), address = primaryAddress.address.trim(), latitude = primaryAddress.latitude.trim(), longitude = primaryAddress.longitude.trim(), initials = createInitials(name), aliases = names.drop(1).map { it.trim() }.filter { it.isNotBlank() }, extraPhones = validPhones.drop(1).map { CustomerPhone(it.number.trim(), if (it.canCall) "Gọi" else if (it.canZalo) "Zalo" else "SMS", it.canCall, it.canZalo, it.canSms) }, extraAddresses = validAddresses.filter { it !== primaryAddress }.map { CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false) }, note = note.trim(), primaryCanCall = primaryPhone.canCall, primaryCanZalo = primaryPhone.canZalo, primaryCanSms = primaryPhone.canSms, photoUri = photoUri))
                }
            }, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("LƯU", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("Hủy") }
        }
    }

    if (showFormPhotoMenu) AlertDialog(
        onDismissRequest = { showFormPhotoMenu = false },
        title = { Text("Ảnh cổng nhà khách") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { showFormPhotoMenu = false; galleryLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Chọn từ thư viện") }
            FilledTonalButton(onClick = {
                showFormPhotoMenu = false
                val file = java.io.File(context.filesDir, "customer_gate_${customer?.id ?: 0L}_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                pendingCameraUri = uri; cameraLauncher.launch(uri)
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Chụp ảnh mới") }
            FilledTonalButton(onClick = { photoUri = ""; showFormPhotoMenu = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE21B1B)); Spacer(Modifier.width(6.dp)); Text("Xóa ảnh", color = Color(0xFFE21B1B)) }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { showFormPhotoMenu = false }) { Text("Hủy") } }
    )

    pickAddressIndex?.let { index ->
        CustomerCoordinateMapPicker(initialPoint = pointFromStrings(addresses[index].latitude, addresses[index].longitude), focusUserLocation = true, onDismiss = { pickAddressIndex = null }, onSavePoint = { point ->
            addresses[index] = addresses[index].copy(latitude = "%.6f".format(java.util.Locale.US, point.latitude), longitude = "%.6f".format(java.util.Locale.US, point.longitude)); pickAddressIndex = null
        })
    }
}

'''
s=s[:start]+new+s[end:]

# Replace placeholder photo card with a URI-aware preview card.
ps=s.index('@Composable\nprivate fun CustomerPhotoCard(')
pe=s.index('@Composable\nprivate fun CustomerCoordinateMapPicker', ps)
photo=r'''@Composable
private fun CustomerPhotoCard(photoUri: String = "") {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (photoUri.isNotBlank()) {
                AndroidView(
                    factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                    update = { image -> runCatching { image.setImageURI(android.net.Uri.parse(photoUri)) }.onFailure { image.setImageDrawable(null) } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, "Ảnh khách hàng", tint = Orange, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(4.dp)); Text("Hình cổng nhà khách", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

'''
s=s[:ps]+photo+s[pe:]
p.write_text(s)
print('Fixed customer form photo menu and actions')
