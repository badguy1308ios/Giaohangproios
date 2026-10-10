package com.example.giaohangpro

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    focusCustomerId: Long? = null,
    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onDeliveryFocusNext: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val context = LocalContext.current
    val ordersSnapshot = vm.orders.toList()
    val customersSnapshot = vm.customers.toList()
    val allGroups = remember(ordersSnapshot, customersSnapshot) {
        buildDeliveryGroups(ordersSnapshot, customersSnapshot)
    }
    val activeGroups = remember(allGroups) {
        allGroups.filterNot { g -> g.orders.all { it.locallyDelivered || isTerminalOrderStatus(it.status) } }
    }
    val deliveredGroups = remember(allGroups) {
        allGroups.filter { g -> g.orders.all { it.locallyDelivered } }
    }
    val terminalGroups = remember(allGroups) {
        allGroups.filter { g -> g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) } }
    }
    val groupHasCoordinate = remember(allGroups) {
        allGroups.associate { it.key to deliveryGroupHasCoordinate(it) }
    }
    // Numbering base = active + locally delivered route stops, in original route order.
    // Terminal VTMan statuses never consume a route STT.
    val numberedRouteGroups = remember(allGroups) {
        allGroups.filterNot { g ->
            g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) }
        }
    }
    var keyword by remember { mutableStateOf("") }
    var mapCustomerFilterId by remember { mutableStateOf<Long?>(null) }
    var showTools by remember { mutableStateOf(false) }
    var showAddOrder by remember { mutableStateOf(false) }
    var editPicker by remember { mutableStateOf(false) }
    var editOrder by remember { mutableStateOf<Order?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleteAllWasChosen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }
    var deliveryFocusCode by remember { mutableStateOf<String?>(null) }
    val q = keyword.trim()

    LaunchedEffect(focusOrderCode, focusCustomerId) {
        val code = focusOrderCode ?: return@LaunchedEffect
        keyword = code
        mapCustomerFilterId = focusCustomerId
    }

    fun matches(g: DeliveryGroup): Boolean =
        mapCustomerFilterId?.let { wantedId -> g.customer?.id == wantedId } ?: (q.isBlank() ||
        g.orders.any { o ->
            o.code.contains(q, true) || o.customer.contains(q, true) ||
                o.phone.contains(q, true) || o.address.contains(q, true) ||
                o.shop.contains(q, true) || o.item.contains(q, true) || matchesCodSearch(o.amount, q)
        } || (g.customer?.name?.contains(q, true) == true))

    // Tab Chi tiết đơn: các điểm CHƯA có tọa độ thật luôn nằm đầu danh sách.
    // Chỉ đổi thứ tự hiển thị; activeGroups vẫn giữ thứ tự tuyến gốc để STT đã tạo không bị lệch.
    val matchedActiveGroups = remember(activeGroups, q, mapCustomerFilterId) { activeGroups.filter(::matches) }
    val pendingGroups = remember(matchedActiveGroups, groupHasCoordinate) {
        matchedActiveGroups.filterNot { groupHasCoordinate[it.key] == true }
    }
    val locatedGroups = remember(matchedActiveGroups, groupHasCoordinate) {
        matchedActiveGroups.filter { groupHasCoordinate[it.key] == true }
    }
    val visibleGroups = remember(pendingGroups, locatedGroups, deliveredGroups, terminalGroups, q, mapCustomerFilterId) {
        pendingGroups + locatedGroups + deliveredGroups.filter(::matches) + terminalGroups.filter(::matches)
    }
    val filteredOrders = remember(ordersSnapshot, q) {
        if (q.isBlank()) ordersSnapshot else ordersSnapshot.filter { o ->
            o.code.contains(q, true) || o.customer.contains(q, true) || o.phone.contains(q, true) ||
                o.address.contains(q, true) || o.shop.contains(q, true) || o.item.contains(q, true) || matchesCodSearch(o.amount, q)
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let {
            keyword = it
            mapCustomerFilterId = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val n = vm.importOrdersCsv(text)
            Toast.makeText(context, "Đã nhập $n đơn từ CSV", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không đọc được CSV", Toast.LENGTH_LONG).show() }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("MVĐ,Shop,SĐT,Tên khách,COD,Địa chỉ,Hàng hóa,Trạng thái,Dịch vụ\n")
                vm.orders.forEach { o ->
                    append(listOf(o.code,o.shop,o.phone,o.customer,o.amount,o.address,o.item,o.status,o.tags.joinToString(" ")).joinToString(",") { csvCell(it) }).append('\n')
                }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context, "Đã xuất ${vm.orders.size} đơn", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context, "Không xuất được danh sách", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.scrollToItem(i)
        onFocusConsumed()
    }

    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Spacer(Modifier.height(6.dp))
            SearchBox(
                keyword,
                { value ->
                    keyword = value
                    mapCustomerFilterId = null
                },
                {
                    keyword = ""
                    mapCustomerFilterId = null
                }
            ) {
                scanLauncher.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")
                    setBeepEnabled(false)
                    setCaptureActivity(PortraitCaptureActivity::class.java)
                    setOrientationLocked(true)
                    setBarcodeImageEnabled(false)
                })
            }
            Spacer(Modifier.height(6.dp))

            if (deleteMode) {
                val allVisibleSelected = filteredOrders.isNotEmpty() && filteredOrders.all { it.code in selected }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allVisibleSelected,
                        onCheckedChange = { all ->
                            deleteAllWasChosen = all
                            selected = if (all) filteredOrders.map { it.code }.toSet() else emptySet()
                        }
                    )
                    Text("Chọn tất cả", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                    TextButton(onClick = {
                        deleteMode = false
                        selected = emptySet()
                        deleteAllWasChosen = false
                    }) { Text("HỦY") }
                    Button(
                        onClick = { if (selected.isNotEmpty()) confirmDelete = true },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("XÓA (${selected.size})")
                    }
                }
            } else {
                Text("${activeGroups.size} điểm giao • ${vm.orders.size} MVĐ", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))

            if (deleteMode) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(bottom = 62.dp)
                ) {
                    items(filteredOrders, key = { it.code }) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = order.code in selected,
                                    onCheckedChange = { checked ->
                                        deleteAllWasChosen = false
                                        selected = if (checked) selected + order.code else selected - order.code
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(order.code, fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                                    Text("${order.customer} • ${order.phone}", color = TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(order.amount, color = MoneyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 62.dp)
                ) {
                    items(visibleGroups, key = { it.key }) { group ->
                        val delivered = group.orders.all { it.locallyDelivered }
                        val routeStt = if (delivered || !vm.routeNumberingEnabled || groupHasCoordinate[group.key] != true) {
                            null
                        } else {
                            vm.routeSttForGroup(group.orders.map(Order::code))
                                ?: numberedRouteGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)
                        }
                        DeliveryGroupCard(
                            routeStt = routeStt,
                            group = group,
                            delivered = delivered,
                            onNumberClick = { group.orders.firstOrNull()?.let(onNumberClick) },
                            onCustomerClick = { group.orders.firstOrNull()?.let(onCustomerClick) },
                            onDelivered = { pendingDeliveredGroup = group },
                            onRedeliver = { group.orders.firstOrNull()?.let { vm.redeliverGroup(it.code) } }
                        )
                    }
                }
            }
        }

        val orderListScope = rememberCoroutineScope()
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { orderListScope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.size(44.dp),
                containerColor = Color.White,
                contentColor = Orange
            ) { Icon(Icons.Default.KeyboardArrowUp, "Về đầu danh sách") }

            FloatingActionButton(
                onClick = { showTools = true },
                modifier = Modifier.size(44.dp),
                containerColor = Orange
            ) { Icon(Icons.Default.Edit, "Công cụ đơn", tint = Color.White) }
        }

        DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }, modifier = Modifier.align(Alignment.BottomStart)) {
            DropdownMenuItem(
                text = { Text("Thêm Đơn Hàng") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { showTools = false; showAddOrder = true }
            )
            DropdownMenuItem(
                text = { Text("Nhập danh sách đơn") },
                leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                onClick = { showTools = false; importLauncher.launch("text/*") }
            )
            DropdownMenuItem(
                text = { Text("Xuất danh sách đơn") },
                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                onClick = { showTools = false; exportLauncher.launch("giaohangpro_orders.csv") }
            )
            DropdownMenuItem(
                text = { Text("Sửa đơn hàng") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { showTools = false; editPicker = true }
            )
            DropdownMenuItem(
                text = { Text("Xóa đơn hàng") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = {
                    showTools = false
                    selected = emptySet()
                    deleteAllWasChosen = false
                    deleteMode = true
                }
            )
        }
    }

    pendingDeliveredGroup?.let { group ->
        val firstCode = group.orders.firstOrNull()?.code.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeliveredGroup = null },
            title = { Text("Xác nhận giao?") },
            text = {
                Text(
                    if (group.orders.size > 1)
                        "Xác nhận thao tác giao cho ${group.orders.size} MVĐ? Nạp lại từng MVĐ sẽ xóa dấu này."
                    else
                        "Xác nhận thao tác giao cho MVĐ $firstCode? Nạp lại MVĐ sẽ xóa dấu này."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentIndex = numberedRouteGroups.indexOfFirst { it.key == group.key }
                    val nextOrder = if (currentIndex >= 0) {
                        numberedRouteGroups.drop(currentIndex + 1)
                            .firstOrNull { next -> next.orders.any { !it.locallyDelivered && !isTerminalOrderStatus(it.status) } }
                            ?.orders?.firstOrNull()
                    } else null
                    group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) }
                    pendingDeliveredGroup = null
                    nextOrder?.let { next ->
                        deliveryFocusCode = next.code
                        onDeliveryFocusNext(next)
                    }
                }) { Text("XÁC NHẬN", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeliveredGroup = null }) { Text("HỦY") }
            }
        )
    }

    if (editPicker) {
        val editQuery = keyword.trim()
        val editCandidates = vm.orders.filter { o ->
            editQuery.isBlank() || o.code.contains(editQuery, true) || o.customer.contains(editQuery, true) ||
                o.phone.contains(editQuery, true) || o.address.contains(editQuery, true) ||
                o.shop.contains(editQuery, true) || o.item.contains(editQuery, true) || matchesCodSearch(o.amount, editQuery)
        }
        AlertDialog(
            onDismissRequest = { editPicker = false },
            title = { Text("Chọn đơn cần sửa") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    editCandidates.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editOrder = o; editPicker = false }.padding(vertical = 9.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(o.code, color = Navy, fontWeight = FontWeight.Bold)
                                Text("${o.customer} • ${o.phone}", fontSize = 11.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (editCandidates.isEmpty()) Text("Không tìm thấy đơn phù hợp", color = TextGray, modifier = Modifier.padding(10.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editPicker = false }) { Text("ĐÓNG") } }
        )
    }

    if (showAddOrder) {
        var code by remember { mutableStateOf("") }
        var shop by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var goods by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("TT500") }
        var cod by remember { mutableStateOf("") }
        var services by remember { mutableStateOf("") }
        var statusMenu by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val matchedCustomer = vm.findCustomerByPhone(phone)
        AlertDialog(
            onDismissRequest = { showAddOrder = false },
            title = { Text("Thêm Đơn Hàng") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(code, { code = it; error = "" }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mã Vận Đơn *") }, singleLine = true)
                    OutlinedTextField(shop, { shop = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên Shop") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Số điện thoại") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    matchedCustomer?.let {
                        Text("Khách: ${it.name}\n${it.address}", color = TextGray, fontSize = 12.sp)
                    }
                    OutlinedTextField(goods, { goods = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hàng hóa") })
                    Box {
                        OutlinedButton(onClick = { statusMenu = true }) { Text("TT: $status") }
                        DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                            AllowedOrderStatuses.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value, color = orderStatusColor(value)) },
                                    onClick = { status = value; statusMenu = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(cod, { cod = it; error = "" }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("COD (đ)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(services, { services = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dịch vụ") }, placeholder = { Text("COD, PXD, XMG") })
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanCode = code.trim()
                    val cleanCod = cod.trim()
                    val digits = cleanCod.filter { it in '0'..'9' }
                    when {
                        cleanCode.isBlank() -> error = "Vui lòng nhập mã vận đơn."
                        cleanCode.any(Char::isWhitespace) -> error = "Mã vận đơn không được chứa khoảng trắng."
                        cleanCod.isNotBlank() && (cleanCod.any { it !in "0123456789., " } ||
                            digits.toLongOrNull() == null) -> error = "COD phải là số tiền nguyên không âm, ví dụ 132000."
                        else -> {
                            val customer = vm.findCustomerByPhone(phone)
                            val order = Order(
                                code = cleanCode, shop = shop.trim(), phone = phone.trim(),
                                customer = customer?.name.orEmpty(), address = customer?.address.orEmpty(),
                                item = goods.trim(), amount = (digits.ifBlank { "0" }.toLong()).toString() + "đ",
                                status = status,
                                tags = services.split(',', ';', '|', ' ', '\n', '\t')
                                    .map(String::trim).filter(String::isNotBlank).distinct()
                            )
                            if (vm.addManualOrder(order)) {
                                keyword = ""
                                showAddOrder = false
                                Toast.makeText(context, "Đã thêm MVĐ $cleanCode", Toast.LENGTH_SHORT).show()
                            } else error = "Mã vận đơn này đã có. Hãy dùng Sửa đơn hàng."
                        }
                    }
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { showAddOrder = false }) { Text("HỦY") } }
        )
    }

    editOrder?.let { original ->
        var shop by remember(original.code) { mutableStateOf(original.shop) }
        var customer by remember(original.code) { mutableStateOf(original.customer) }
        var phone by remember(original.code) { mutableStateOf(original.phone) }
        var address by remember(original.code) { mutableStateOf(original.address) }
        var amount by remember(original.code) { mutableStateOf(original.amount) }
        var item by remember(original.code) { mutableStateOf(original.item) }
        var services by remember(original.code) { mutableStateOf(original.tags.joinToString(" ")) }
        AlertDialog(
            onDismissRequest = { editOrder = null },
            title = { Text("Sửa ${original.code}") },
            text = {
                Column(
                    Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(shop, { shop = it }, label = { Text("Tên Shop") }, singleLine = true)
                    OutlinedTextField(customer, { customer = it }, label = { Text("Tên khách") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("SĐT") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Địa chỉ") })
                    OutlinedTextField(amount, { amount = it }, label = { Text("COD") }, singleLine = true)
                    OutlinedTextField(item, { item = it }, label = { Text("Hàng hóa") })
                    OutlinedTextField(services, { services = it }, label = { Text("Dịch vụ") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = services.split(Regex("[\\s,;]+" )).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    vm.updateOrder(original.copy(
                        shop = shop,
                        customer = customer,
                        phone = phone,
                        address = address,
                        amount = amount,
                        item = item,
                        tags = tags
                    ))
                    editOrder = null
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { editOrder = null }) { Text("HỦY") } }
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xóa đơn hàng") },
        text = { Text("Xác nhận xóa ${selected.size} đơn đã chọn?") },
        confirmButton = {
            TextButton(onClick = {
                val clearAllRouteMemory = deleteAllWasChosen && selected.isNotEmpty()
                vm.deleteOrders(selected)
                if (clearAllRouteMemory) vm.clearRouteNumbering()
                selected = emptySet()
                deleteAllWasChosen = false
                confirmDelete = false
                deleteMode = false
            }) { Text("XÓA", color = Color(0xFFE21B1B)) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("HỦY") } }
    )
}
