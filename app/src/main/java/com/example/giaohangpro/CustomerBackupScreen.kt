package com.example.giaohangpro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun CustomerBackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(CustomerLocalSync.portableStatus(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var autoSync by remember(refresh) { mutableStateOf(CustomerLocalSync.isEnabled(context)) }
    var times by remember(refresh) { mutableStateOf(CustomerLocalSync.scheduledTimes(context)) }
    var pendingRestore by remember { mutableStateOf<CustomerSyncSession?>(null) }
    val sessions = remember(refresh) { CustomerLocalSync.sessions(context) }
    val dateFmt = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()) }
    var portableReady by remember(refresh) { mutableStateOf(CustomerLocalSync.portableUri(context) != null) }

    val createSyncFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang tạo file đồng bộ..."
            runCatching {
                CustomerLocalSync.setPortableUri(context, uri)
                withContext(Dispatchers.IO) {
                    CustomerLocalSync.syncNow(context)
                }
            }.onSuccess {
                portableReady = true; refresh++
                status = CustomerLocalSync.portableStatus(context)
            }.onFailure { e -> status = "Không tạo được file đồng bộ: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }

    val openSyncFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang khôi phục từ file đồng bộ..."
            runCatching { withContext(Dispatchers.IO) { CustomerLocalSync.importPortableBackup(context, uri) } }
                .onSuccess { count ->
                    vm.reloadPersistentData(); portableReady = true; refresh++
                    status = CustomerLocalSync.portableStatus(context)
                }
                .onFailure { e -> status = "Không đọc được file đồng bộ: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }

    fun doSync() {
        scope.launch {
            busy = true; status = "Đang đồng bộ thay đổi..."
            runCatching { withContext(Dispatchers.IO) { CustomerLocalSync.syncNow(context) } }
                .onSuccess { s ->
                    status = if (s == null) "Dữ liệu không thay đổi — không tạo phiên mới" else "Đồng bộ xong: " + s.upserts + " cập nhật, " + s.deletes + " xóa • " + s.customerCount + " khách"
                    status += "\n" + if (CustomerLocalSync.portableUri(context) != null) CustomerLocalSync.portableStatus(context) else "Chưa liên kết file bên ngoài."
                    refresh++
                }
                .onFailure { e -> status = "Đồng bộ lỗi: " + (e.message ?: "không xác định") }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang nhập ZIP dự phòng..."
            runCatching { vm.importCustomerBackup(context, uri) }
                .onSuccess { x -> status = "Nhập xong: +" + x.added + " khách mới, cập nhật " + x.updated }
                .onFailure { e -> status = "Không nhập được ZIP: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang xuất ZIP đầy đủ..."
            runCatching { vm.exportCustomerBackup(context, uri) }
                .onSuccess { x -> status = "Xuất ZIP xong " + x.total + " khách, " + x.images + " ảnh" }
                .onFailure { e -> status = "Không xuất được ZIP: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("ĐỒNG BỘ KHÁCH HÀNG", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("BACKUP CỤC BỘ", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Chỉ lưu khách hàng thêm / sửa / xóa kể từ phiên trước. Dữ liệu nằm trên điện thoại, chưa gửi lên Internet.", color = TextGray, fontSize = 12.sp)
                    val last = sessions.firstOrNull()
                    Text(if (last == null) "Chưa có phiên đồng bộ" else "Phiên nội bộ gần nhất: " + dateFmt.format(java.util.Date(last.createdAt)) + " • " + last.customerCount + " khách", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(enabled = !busy, onClick = { doSync() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("ĐỒNG BỘ NGAY", fontWeight = FontWeight.Bold)
            }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (portableReady) Color(0xFFE6F4EA) else OrangeLight)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (portableReady) "FILE ĐỒNG BỘ ĐÃ LIÊN KẾT" else "CHỌN NƠI LƯU FILE ĐỒNG BỘ", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(if (portableReady) "File nằm ngoài dữ liệu ứng dụng nên vẫn còn khi gỡ GiaoHangPro." else "Chọn một nơi lưu bên ngoài ứng dụng. Chỉ cần chọn một lần; các phiên sau app tự cập nhật file.", color = TextGray, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { createSyncFileLauncher.launch("GiaoHangPro_DongBoKhachHang.zip") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text(if (portableReady) "ĐỔI FILE" else "TẠO FILE", fontSize = 11.sp)
                        }
                        OutlinedButton(enabled = !busy, onClick = { openSyncFileLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Restore, null); Spacer(Modifier.width(4.dp)); Text("CHỌN FILE CŨ", fontSize = 11.sp)
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TỰ ĐỘNG THEO LỊCH", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (autoSync) "Lần kế tiếp: " + (CustomerLocalSync.nextSchedule(context) ?: "--") else "Đang tắt", color = TextGray, fontSize = 11.sp)
                        }
                        Switch(checked = autoSync, onCheckedChange = { autoSync = it; CustomerLocalSync.setEnabled(context, it); refresh++ })
                    }
                    times.forEach { time ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Orange, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                            Text(time, modifier = Modifier.weight(1f), color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { times = times - time; CustomerLocalSync.setScheduledTimes(context, times); refresh++ }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.DeleteOutline, "Xóa giờ", tint = TextGray) }
                        }
                    }
                    OutlinedButton(onClick = {
                        val now = java.util.Calendar.getInstance()
                        android.app.TimePickerDialog(context, { _, h, m ->
                            val t = String.format(java.util.Locale.US, "%02d:%02d", h, m)
                            times = (times + t).distinct().sorted(); CustomerLocalSync.setScheduledTimes(context, times); refresh++
                        }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.AddAlarm, null); Spacer(Modifier.width(6.dp)); Text("THÊM GIỜ ĐỒNG BỘ") }
                }
            }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = OrangeLight)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) { CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp); Spacer(Modifier.width(9.dp)) }
                    Text(status, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("LỊCH SỬ ĐỒNG BỘ", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (sessions.isEmpty()) Text("Chưa có dữ liệu. Bấm Đồng bộ ngay để tạo bản gốc đầu tiên.", color = TextGray, fontSize = 12.sp)
            sessions.forEachIndexed { index, s ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (index == 0) Icons.Default.Verified else Icons.Default.History, null, tint = if (index == 0) Color(0xFF2E7D32) else TextGray); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dateFmt.format(java.util.Date(s.createdAt)), color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(s.customerCount.toString() + " khách • +/sửa " + s.upserts + " • xóa " + s.deletes + if (index == 0) " • Bản an toàn" else "", color = TextGray, fontSize = 11.sp)
                        }
                        if (index != 0) TextButton(enabled = !busy, onClick = { pendingRestore = s }) { Text("KHÔI PHỤC", fontSize = 11.sp) }
                    }
                }
            }
            HorizontalDivider(color = Border)
            Text("IMPORT / EXPORT THỦ CÔNG", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Giữ ZIP đầy đủ để chuyển máy hoặc lưu ra nơi khác khi cần.", color = TextGray, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(4.dp)); Text("NHẬP ZIP", fontSize = 11.sp) }
                OutlinedButton(enabled = !busy, onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                    exportLauncher.launch("GiaoHangPro_KhachHang_" + stamp + ".zip")
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(4.dp)); Text("XUẤT ZIP", fontSize = 11.sp) }
            }
        }
    }
    pendingRestore?.let { s ->
        AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Khôi phục phiên này?") },
            text = { Text(dateFmt.format(java.util.Date(s.createdAt)) + " • " + s.customerCount + " khách\n\nDữ liệu hiện tại sẽ được đồng bộ bảo vệ trước khi khôi phục.") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                scope.launch {
                    busy = true; status = "Đang khôi phục..."
                    runCatching { withContext(Dispatchers.IO) { CustomerLocalSync.restore(context, s.id) } }
                        .onSuccess { count -> vm.reloadPersistentData(); status = "Đã khôi phục " + count + " khách"; refresh++ }
                        .onFailure { e -> status = "Khôi phục lỗi: " + (e.message ?: "không xác định") + " • Dữ liệu hiện tại không bị xóa" }
                    busy = false
                }
            }) { Text("KHÔI PHỤC") } }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("HỦY") } })
    }
}
