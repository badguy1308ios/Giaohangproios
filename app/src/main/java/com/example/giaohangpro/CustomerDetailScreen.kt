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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ================================================================
// 8. CHI TIẾT KHÁCH HÀNG
// ================================================================
@Composable
fun CustomerDetailScreen(
    customer: Customer,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPhotoChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val photoScope = rememberCoroutineScope()
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
        photoScope.launch {
            val stored = storeGatePhoto(context, uri)
            if (stored != null) onPhotoChanged(stored)
            else Toast.makeText(context, "Không lưu được ảnh đã chọn. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
        }
    }

    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val captured = pendingCameraUri
        pendingCameraUri = null
        if (ok && captured != null) photoScope.launch {
            val stored = storeGatePhoto(context, android.net.Uri.parse(captured))
            if (stored != null) onPhotoChanged(stored)
            else Toast.makeText(context, "Ảnh chụp chưa lưu được. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
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
                onPhotoClick = { showPhotoMenu = true }
            ) {
                CustomerSideActions(
                    modifier = Modifier.padding(bottom = 3.dp),
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
                    onClick = {
                        showPhotoMenu = false
                        val file = java.io.File(context.filesDir, "customer_gate_${customer.id}_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                        pendingCameraUri = uri.toString()
                        cameraLauncher.launch(uri)
                    },
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

@Composable
internal fun CustomerPageHeader(title: String, onBack: () -> Unit) {
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
        modifier = Modifier.size(width = 45.dp, height = 42.dp).clickable { onClick() },
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
private fun CustomerDetailContent(
    customer: Customer,
    modifier: Modifier = Modifier,
    onPhotoClick: () -> Unit,
    actions: @Composable () -> Unit
) {
    val photo by rememberGatePhoto(customer.photoUri)
    val bitmap = photo.bitmap
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onPhotoClick).then(
                if (bitmap != null) Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                else Modifier.height(306.dp)
            ), RoundedCornerShape(14.dp),
            CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), "Cổng nhà khách", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else if (photo.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storefront, "Cổng nhà khách", tint = Orange, modifier = Modifier.size(122.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (customer.photoUri.isBlank()) "Hình cổng nhà khách" else "Không đọc được ảnh. Chạm để chọn lại.",
                            color = TextGray, fontSize = 13.sp
                        )
                    }
                }

            }
        }
        // Actions and information share the same scrolling block below the full photo.
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (listOf(customer.name) + customer.aliases).filter { it.isNotBlank() }.forEach { displayName ->
                    Box(Modifier.padding(start = 50.dp)) { DetailInfoCard(Icons.Default.Person, displayName) }
                }
                (listOf(customer.phone) + customer.extraPhones.map { it.number }).filter { it.isNotBlank() }.forEach { displayPhone ->
                    Box(Modifier.padding(start = 50.dp)) { DetailInfoCard(Icons.Default.Phone, displayPhone) }
                }
                if (customer.streetName.isNotBlank()) {
                    Box(Modifier.padding(start = 50.dp)) {
                        DetailInfoCard(Icons.Default.Map, "Tên đường: ${customer.streetName}")
                    }
                }
                (listOf(CustomerAddress(customer.address, customer.latitude, customer.longitude, true)) + customer.extraAddresses).filter { it.address.isNotBlank() }.forEach { addr ->
                    Card(
                        Modifier.fillMaxWidth().padding(start = 50.dp), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(addr.address, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(if (addr.latitude.isBlank() || addr.longitude.isBlank()) "Chưa có tọa độ" else "${addr.latitude}, ${addr.longitude}", color = TextGray, fontSize = 11.sp)
                            }
                        }
                    }
                }
                if (customer.note.isNotBlank()) Card(
                    Modifier.fillMaxWidth().padding(start = 50.dp), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) { Text(customer.note, Modifier.padding(7.dp), color = Navy, fontSize = 13.sp) }
            }
            actions()
        }
    }
}

@Composable
private fun DetailInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(value, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
