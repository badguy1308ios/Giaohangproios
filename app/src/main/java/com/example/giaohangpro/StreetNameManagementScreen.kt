package com.example.giaohangpro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StreetNameManagementScreen(vm: MainViewModel, onBack: () -> Unit) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingEdit by remember { mutableStateOf<String?>(null) }
    var editedName by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    val streetNames = vm.streetNames
        .sortedBy { it.lowercase(java.util.Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("QUẢN LÝ TÊN ĐƯỜNG", onBack)
        if (streetNames.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Map, null, tint = TextGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Chưa có tên đường nào", color = TextGray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(streetNames, key = { it.lowercase(java.util.Locale.getDefault()) }) { streetName ->
                    val customerCount = vm.customers.count {
                        it.streetName.trim().equals(streetName, ignoreCase = true)
                    }
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(OrangeLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Map, null, tint = Orange, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(streetName, color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "$customerCount khách hàng đang chọn",
                                    color = TextGray,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { pendingEdit = streetName; editedName = streetName; editError = null },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Edit, "Sửa tên đường", tint = Orange, modifier = Modifier.size(22.dp))
                            }
                            IconButton(
                                onClick = { pendingDelete = streetName },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    "Xóa tên đường",
                                    tint = Color(0xFFE21B1B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingEdit?.let { streetName ->
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text("Sửa tên đường") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it; editError = null },
                        label = { Text("Tên đường") },
                        singleLine = true,
                        isError = editError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    editError?.let { Text(it, color = Color(0xFFE21B1B)) }
                    Text("Tên mới sẽ được cập nhật cho tất cả khách hàng đang chọn đường này và danh sách chọn khi thêm/sửa khách.", fontSize = 12.sp, color = TextGray)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editedName.isNotBlank(),
                    onClick = {
                        editError = vm.renameStreetName(streetName, editedName)
                        if (editError == null) pendingEdit = null
                    }
                ) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { pendingEdit = null }) { Text("HỦY") } }
        )
    }

    pendingDelete?.let { streetName ->
        val customerCount = vm.customers.count {
            it.streetName.trim().equals(streetName, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xóa tên đường") },
            text = {
                Text(
                    "Xóa \"$streetName\"? $customerCount khách hàng đang chọn tên đường này sẽ trở về trạng thái chưa chọn. Địa chỉ và tọa độ vẫn được giữ nguyên."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStreetName(streetName)
                    pendingDelete = null
                }) {
                    Text("XÓA", color = Color(0xFFE21B1B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("HỦY") }
            }
        )
    }
}
