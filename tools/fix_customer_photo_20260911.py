from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# 1) Customer model: persist selected/captured customer gate photo URI.
old = '''    val primaryCanCall: Boolean = true,\n    val primaryCanZalo: Boolean = true,\n    val primaryCanSms: Boolean = true\n)'''
new = '''    val primaryCanCall: Boolean = true,\n    val primaryCanZalo: Boolean = true,\n    val primaryCanSms: Boolean = true,\n    val photoUri: String = \"\"\n)'''
if 'val photoUri: String = ""' not in s:
    if old not in s:
        raise SystemExit('Customer model anchor not found')
    s = s.replace(old, new, 1)

# 2) Wire photo updates from detail screen back into ViewModel.
old = '''                onBack = { screen = AppScreen.MAIN },\n                onEdit = { formIsNew = false; screen = AppScreen.CUSTOMER_FORM },\n                onDelete = { vm.deleteCustomer(c.id); selectedCustomerId = null; screen = AppScreen.MAIN }\n            )'''
new = '''                onBack = { screen = AppScreen.MAIN },\n                onEdit = { formIsNew = false; screen = AppScreen.CUSTOMER_FORM },\n                onPhotoChanged = { uri -> vm.updateCustomer(c.copy(photoUri = uri)) },\n                onDelete = { vm.deleteCustomer(c.id); selectedCustomerId = null; screen = AppScreen.MAIN }\n            )'''
if 'onPhotoChanged = { uri -> vm.updateCustomer(c.copy(photoUri = uri)) }' not in s:
    if old not in s:
        raise SystemExit('detail screen wiring anchor not found')
    s = s.replace(old, new, 1)

# 3) Keep photo when saving customer edit form.
old = '''                            primaryCanCall = primaryPhone.canCall,\n                            primaryCanZalo = primaryPhone.canZalo,\n                            primaryCanSms = primaryPhone.canSms\n                        ))'''
new = '''                            primaryCanCall = primaryPhone.canCall,\n                            primaryCanZalo = primaryPhone.canZalo,\n                            primaryCanSms = primaryPhone.canSms,\n                            photoUri = customer?.photoUri.orEmpty()\n                        ))'''
if 'photoUri = customer?.photoUri.orEmpty()' not in s:
    if old not in s:
        raise SystemExit('customer save anchor not found')
    s = s.replace(old, new, 1)

# 4) Replace CustomerDetailScreen with image picker/camera launchers and persistent photo update.
start = s.find('@Composable\nfun CustomerDetailScreen(')
end = s.find('\n@Composable\nprivate fun CustomerPageHeader', start)
if start == -1 or end == -1:
    raise SystemExit('CustomerDetailScreen block not found')
new_detail = r'''@Composable
fun CustomerDetailScreen(
    customer: Customer,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPhotoChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        onPhotoChanged(uri.toString())
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        runCatching {
            val file = java.io.File(context.filesDir, "customer_gate_${customer.id}.jpg")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            onPhotoChanged(android.net.Uri.fromFile(file).toString())
        }.onFailure {
            Toast.makeText(context, "Không lưu được ảnh vừa chụp", Toast.LENGTH_SHORT).show()
        }
    }

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
            CustomerDetailContent(
                customer = customer,
                modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                onEditPhoto = { showPhotoMenu = true }
            )
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

    if (showPhotoMenu) AlertDialog(
        onDismissRequest = { showPhotoMenu = false },
        title = { Text("Ảnh cổng nhà khách") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { showPhotoMenu = false; galleryLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text("Chọn từ thư viện")
                }
                FilledTonalButton(
                    onClick = { showPhotoMenu = false; cameraLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text("Chụp ảnh mới")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showPhotoMenu = false }) { Text("Hủy") } }
    )

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Xóa khách hàng") },
        text = { Text("Bạn có chắc muốn xóa ${customer.name} không?") },
        confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Xóa", color = Color(0xFFE21B1B)) } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") } }
    )
}
'''
s = s[:start] + new_detail + s[end:]

# 5) Replace customer detail content: 70% taller gate-photo area + pencil button + photo preview.
start = s.find('@Composable\nprivate fun CustomerDetailContent(')
end = s.find('\n@Composable\nprivate fun DetailInfoCard', start)
if start == -1 or end == -1:
    raise SystemExit('CustomerDetailContent block not found')
new_content = r'''@Composable
private fun CustomerDetailContent(
    customer: Customer,
    modifier: Modifier = Modifier,
    onEditPhoto: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Card(
            Modifier.fillMaxWidth().height(306.dp), RoundedCornerShape(14.dp),
            CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (customer.photoUri.isNotBlank()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                        update = { imageView ->
                            val uri = android.net.Uri.parse(customer.photoUri)
                            val bitmap = runCatching {
                                when (uri.scheme) {
                                    "file" -> android.graphics.BitmapFactory.decodeFile(uri.path)
                                    else -> context.contentResolver.openInputStream(uri)?.use(android.graphics.BitmapFactory::decodeStream)
                                }
                            }.getOrNull()
                            if (bitmap != null) imageView.setImageBitmap(bitmap) else imageView.setImageDrawable(null)
                        }
                    )
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storefront, "Cổng nhà khách", tint = Orange, modifier = Modifier.size(122.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Hình cổng nhà khách", color = TextGray, fontSize = 13.sp)
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(48.dp).clickable { onEditPhoto() },
                    shape = CircleShape,
                    color = Orange,
                    shadowElevation = 4.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, "Thêm hoặc đổi ảnh", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
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
'''
s = s[:start] + new_content + s[end:]

# 6) Person + phone icons same size as address icon.
old = '''            Box(Modifier.size(46.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {\n                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))\n            }\n            Spacer(Modifier.width(7.dp))'''
new = '''            Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {\n                Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))\n            }\n            Spacer(Modifier.width(6.dp))'''
if old in s:
    s = s.replace(old, new, 1)
elif 'Modifier.size(34.dp).clip(CircleShape).background(Orange)' not in s:
    raise SystemExit('DetailInfoCard icon size anchor not found')

p.write_text(s, encoding='utf-8')
print('Applied customer photo picker/camera and detail icon sizing changes')
