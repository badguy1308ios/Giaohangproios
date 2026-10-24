package com.example.giaohangpro

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

// ================================================================
// 9. THÊM / SỬA KHÁCH HÀNG
// ================================================================
private data class PhoneDraft(val number: String, val canCall: Boolean, val canZalo: Boolean, val canSms: Boolean)
private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean, val photoUri: String = "")

@Composable
fun CustomerFormScreen(
    customer: Customer?,
    streetNameOptions: List<String> = emptyList(),
    onStreetNameCreated: (String) -> Unit = {},
    onBack: () -> Unit,
    onSave: (Customer, Customer?) -> Unit
) {
    val original = remember(customer?.id) { customer }
    var pendingCoordinateSave by remember(customer?.id) { mutableStateOf<Customer?>(null) }
    var coordinateError by remember(customer?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var streetName by remember(customer?.id) { mutableStateOf(customer?.streetName.orEmpty()) }
    var note by remember(customer?.id) { mutableStateOf(customer?.note.orEmpty()) }
    val addresses = remember(customer?.id) {
        mutableStateListOf<AddressDraft>().apply {
            if (customer == null) add(AddressDraft("", "", "", true)) else {
                add(AddressDraft(customer.address, customer.latitude, customer.longitude, true, customer.photoUri))
                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false, it.photoUri) })
            }
        }
    }
    val photoUri = addresses.firstOrNull { it.isPrimary }?.photoUri.orEmpty()
    var importingPhoto by remember { mutableStateOf(false) }
    val photoScope = rememberCoroutineScope()
    fun setPrimaryPhoto(uri: String) {
        val index = addresses.indexOfFirst { it.isPrimary }
        if (index >= 0) {
            val previous = addresses[index].photoUri
            addresses[index] = addresses[index].copy(photoUri = uri)
            // An intermediate draft photo was never saved as the customer's photo.
            val savedPhotos = listOf(original?.photoUri.orEmpty()) + original?.extraAddresses.orEmpty().map { it.photoUri }
            if (previous.isNotBlank() && previous != uri && previous !in savedPhotos) {
                photoScope.launch(kotlinx.coroutines.Dispatchers.IO) { deleteStoredGatePhoto(context, previous) }
            }
        }
    }
    val names = remember(customer?.id) { mutableStateListOf<String>().apply { add(customer?.name.orEmpty()); addAll(customer?.aliases.orEmpty()) } }
    var expandedPhone by remember { mutableStateOf<Int?>(null) }
    var pickAddressIndex by remember { mutableStateOf<Int?>(null) }
    var validation by remember { mutableStateOf(false) }
    var showFormPhotoMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        importingPhoto = true
        photoScope.launch {
            try {
                val stored = storeGatePhoto(context, uri)
                if (stored != null) setPrimaryPhoto(stored)
                else Toast.makeText(context, "Không lưu được ảnh đã chọn. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
            } finally { importingPhoto = false }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val captured = pendingCameraUri
        pendingCameraUri = null
        if (ok && captured != null) {
            importingPhoto = true
            photoScope.launch {
                try {
                    val stored = storeCapturedGatePhoto(context, android.net.Uri.parse(captured))
                    if (stored != null) setPrimaryPhoto(stored)
                    else Toast.makeText(context, "Ảnh chụp chưa lưu được. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
                } finally { importingPhoto = false }
            }
        }
    }

    val phones = remember(customer?.id) {
        mutableStateListOf<PhoneDraft>().apply {
            if (customer == null) add(PhoneDraft("", true, true, true)) else {
                add(PhoneDraft(customer.phone, customer.primaryCanCall, customer.primaryCanZalo, customer.primaryCanSms))
                addAll(customer.extraPhones.map { PhoneDraft(it.number, it.canCall, it.canZalo, it.canSms) })
            }
        }
    }


    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader(if (customer == null) "THÊM KHÁCH HÀNG" else "SỬA KHÁCH HÀNG", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                CustomerPhotoCard(photoUri)
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp).size(38.dp).clickable(enabled = !importingPhoto) { showFormPhotoMenu = true }, CircleShape, color = Orange) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, "Quản lý ảnh", tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
            }
            if (importingPhoto) Text("Đang lưu ảnh…", color = TextGray, fontSize = 12.sp)
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
            StreetNameDropdown(
                value = streetName,
                onValueChange = { streetName = it },
                options = streetNameOptions,
                onCreate = onStreetNameCreated
            )
            addresses.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.isPrimary, enabled = !importingPhoto, onCheckedChange = { checked -> if (checked) addresses.indices.forEach { i -> addresses[i] = addresses[i].copy(isPrimary = i == index) } }, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(3.dp))
                            OutlinedTextField(value = item.address, onValueChange = { addresses[index] = item.copy(address = it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Địa chỉ", fontSize = 12.sp) })
                            SmallPlusMinus(plus = true) { addresses.add(index + 1, AddressDraft("", "", "", false)) }
                            if (addresses.size > 1 && !importingPhoto) SmallPlusMinus(plus = false) {
                                val wasPrimary = addresses[index].isPrimary; addresses.removeAt(index)
                                if (wasPrimary && addresses.isNotEmpty()) addresses[0] = addresses[0].copy(isPrimary = true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fun setCoord(raw: String, latitudeField: Boolean) {
                                val (lat, lng) = CustomerCoordinateSafety.input(raw, latitudeField, addresses[index].latitude, addresses[index].longitude)
                                addresses[index] = addresses[index].copy(latitude = lat, longitude = lng)
                            }
                            OutlinedTextField(item.latitude, { setCoord(it, true) }, Modifier.weight(1f), singleLine = true, label = { Text("Vĩ độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(item.longitude, { setCoord(it, false) }, Modifier.weight(1f), singleLine = true, label = { Text("Kinh độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            TextButton(onClick = { pickAddressIndex = index }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(2.dp)); Text("Chọn trên bản đồ", fontSize = 11.sp) }
                        }
                    }
                }
            }

            CompactInput("Ghi chú", note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
            if (coordinateError) Text("Tọa độ phải đủ cả vĩ độ và kinh độ hợp lệ, hoặc để trống cả hai.", color = Color(0xFFE21B1B), fontSize = 12.sp)
            if (validation) Text("Cần nhập tên, ít nhất 1 SĐT và 1 địa chỉ.", color = Color(0xFFE21B1B), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                val validPhones = phones.filter { it.number.isNotBlank() }; val validAddresses = addresses.filter { it.address.isNotBlank() }
                if (name.isBlank() || validPhones.isEmpty() || validAddresses.isEmpty()) validation = true else {
                    val primaryAddress = validAddresses.firstOrNull { it.isPrimary } ?: validAddresses.first(); val primaryPhone = validPhones.first()
                    val edited = Customer(id = customer?.id ?: 0L, name = name.trim(), phone = primaryPhone.number.trim(), address = primaryAddress.address.trim(), streetName = streetName.trim(), latitude = primaryAddress.latitude.trim(), longitude = primaryAddress.longitude.trim(), initials = createInitials(name), aliases = names.drop(1).map { it.trim() }.filter { it.isNotBlank() }, extraPhones = validPhones.drop(1).map { CustomerPhone(it.number.trim(), if (it.canCall) "Gọi" else if (it.canZalo) "Zalo" else "SMS", it.canCall, it.canZalo, it.canSms) }, extraAddresses = validAddresses.filter { it !== primaryAddress }.map { CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false, it.photoUri) }, note = note.trim(), primaryCanCall = primaryPhone.canCall, primaryCanZalo = primaryPhone.canZalo, primaryCanSms = primaryPhone.canSms, photoUri = primaryAddress.photoUri)
                    // Existing malformed legacy coordinates may be preserved by unrelated edits.
                    val positions = CustomerCoordinateSafety.positions(edited)
                    val oldPositions = original?.let(CustomerCoordinateSafety::positions).orEmpty()
                    coordinateError = positions.any { (lat, lng) ->
                        (lat.isNotBlank() || lng.isNotBlank()) && !CustomerCoordinateSafety.valid(lat, lng) && (lat to lng) !in oldPositions
                    }
                    if (!coordinateError) {
                        if (original != null && positions != oldPositions) pendingCoordinateSave = edited
                        else onSave(edited, original)
                    }
                }
            }, enabled = !importingPhoto, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("LƯU", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("Hủy") }
        }
    }

    pendingCoordinateSave?.let { edited ->
        fun describe(value: Customer): String = CustomerCoordinateSafety.positions(value).mapIndexed { index, (lat, lng) ->
            "${if (index == 0) "Chính" else "Phụ $index"}: ${if (lat.isBlank() && lng.isBlank()) "Chưa có tọa độ" else "$lat, $lng"}"
        }.joinToString("\n")
        AlertDialog(
            onDismissRequest = { pendingCoordinateSave = null },
            title = { Text("Xác nhận đổi tọa độ khách") },
            text = { Text("Đã lưu:\n${describe(requireNotNull(original))}\n\nSẽ lưu:\n${describe(edited)}", modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { pendingCoordinateSave = null; onSave(edited, original) }) { Text("Đổi tọa độ và lưu") } },
            dismissButton = { TextButton(onClick = { pendingCoordinateSave = null }) { Text("Quay lại kiểm tra") } }
        )
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
                pendingCameraUri = uri.toString(); cameraLauncher.launch(uri)
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Chụp ảnh mới") }
            FilledTonalButton(onClick = { setPrimaryPhoto(""); showFormPhotoMenu = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE21B1B)); Spacer(Modifier.width(6.dp)); Text("Xóa ảnh", color = Color(0xFFE21B1B)) }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { showFormPhotoMenu = false }) { Text("Hủy") } }
    )

    pickAddressIndex?.let { index ->
        CustomerCoordinateMapPicker(initialPoint = pointFromStrings(addresses[index].latitude, addresses[index].longitude), focusUserLocation = true, onDismiss = { pickAddressIndex = null }, onSavePoint = { point ->
            addresses[index] = addresses[index].copy(latitude = "%.6f".format(java.util.Locale.US, point.latitude), longitude = "%.6f".format(java.util.Locale.US, point.longitude)); pickAddressIndex = null
        })
    }
}

@Composable
private fun StreetNameDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onCreate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val typed = value.trim()
    val available = options
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(java.util.Locale.getDefault()) }
    val filtered = available.filter { typed.isBlank() || it.contains(typed, ignoreCase = true) }
    val hasExactMatch = available.any { it.equals(typed, ignoreCase = true) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tên Đường", fontSize = 11.sp) },
            placeholder = { Text("Nhập hoặc chọn tên đường", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        "Mở danh sách tên đường"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onValueChange(typed)
                if (typed.isNotBlank() && !hasExactMatch) onCreate(typed)
                expanded = false
            })
        )
        DropdownMenu(
            expanded = expanded && (filtered.isNotEmpty() || typed.isNotBlank()),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 220.dp),
            properties = PopupProperties(focusable = false)
        ) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
            if (typed.isNotBlank() && !hasExactMatch) {
                if (filtered.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("+ Tạo \"$typed\"", color = Orange, fontWeight = FontWeight.Bold) },
                    onClick = {
                        onValueChange(typed)
                        onCreate(typed)
                        expanded = false
                    }
                )
            }
        }
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
private fun CustomerPhotoCard(photoUri: String = "") {
    val photo by rememberGatePhoto(photoUri)
    val bitmap = photo.bitmap
    Card(modifier = Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), "Cổng nhà khách", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (photo.loading) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, "Ảnh khách hàng", tint = Orange, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(if (photoUri.isBlank()) "Hình cổng nhà khách" else "Không đọc được ảnh. Bấm nút ảnh để chọn lại.", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
