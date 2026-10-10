package com.example.giaohangpro

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Tab bản đồ hiển thị tất cả đơn hàng bằng marker kiểu bong bóng có đánh số thứ tự.
// Nếu đơn hàng/khách chưa có tọa độ, marker vẫn xuất hiện tạm tại vị trí GPS tài xế để người dùng biết đơn đó đang chờ bổ sung tọa độ.
@Composable
fun MapScreen(
    vm: MainViewModel,
    active: Boolean,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenOrder: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // Locally delivered stops stay in the numbering base so later STT values never collapse upward.
    // They are removed only from the visible/active map route.
    val numberedRouteGroups = buildDeliveryGroups(
        vm.orders.filterNot { isTerminalOrderStatus(it.status) },
        vm.customers
    )
    val activeGroups = numberedRouteGroups.filterNot { g -> g.orders.all { it.locallyDelivered } }
    LaunchedEffect(numberedRouteGroups.map { g -> g.orders.map(Order::code) }, vm.routeNumberingEnabled) {
        if (vm.routeNumberingEnabled) {
            vm.ensureRouteSttGroups(numberedRouteGroups.map { g -> g.orders.map(Order::code) })
        }
    }
    val stableRouteNumbers = numberedRouteGroups.flatMapIndexed { index, group ->
        val stableStt = vm.routeSttForGroup(group.orders.map(Order::code)) ?: (index + 1)
        group.orders.map { it.code to stableStt }
    }.toMap()
    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember(activeGroups.map { it.key }) { mutableStateOf(activeGroups.map { it.key }) }
    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }
    val streetRouteScope = rememberCoroutineScope()
    var streetRouteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(Unit) { onDispose { streetRouteJob?.cancel() } }

    fun commitGroupedDirectStt() {
        val marker = editMarker ?: return
        val maxStt = draft.size.coerceAtLeast(1)
        val target = editNumberText.toIntOrNull()?.coerceIn(1, maxStt) ?: return
        val groupKey = activeGroups.firstOrNull { g -> g.orders.any { it.code == marker.order.code } }?.key ?: return
        val current = draft.indexOf(groupKey)
        if (current >= 0) {
            val next = draft.toMutableList()
            val moving = next.removeAt(current)
            next.add((target - 1).coerceIn(0, next.size), moving)
            // Edit STT only in the local draft. Persist/rebuild the route once when Save is pressed.
            draft = next
        }
        editMarker = null
        editNumberText = ""
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("STT,MVĐ\n")
                activeGroups.forEachIndexed { i, g -> append("${i + 1},${csvCell(g.orders.joinToString(" ") { it.code })}\n") }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context, "Đã xuất ${activeGroups.size} điểm giao", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context, "Không xuất được STT", Toast.LENGTH_LONG).show() }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val codes = text.lineSequence().drop(1).mapNotNull { line -> line.substringAfter(',', "").trim().trim('"').split(' ').firstOrNull()?.takeIf(String::isNotBlank) }.toList()
            val byRep = activeGroups.associateBy { it.orders.first().code }
            streetRouteJob?.cancel()
            val orderedGroups = codes.mapNotNull { byRep[it] }
            val remainingGroups = activeGroups.filterNot { it.orders.first().code in codes }
            val finalGroups = orderedGroups + remainingGroups
            vm.reorderOrders(finalGroups.flatMap { it.orders.map(Order::code) })
            vm.replaceRouteStt(finalGroups.map { it.orders.map(Order::code) })
        }.onFailure { Toast.makeText(context, "Không đọc được file STT", Toast.LENGTH_LONG).show() }
    }

    val orderedGroups = if (editing) draft.mapNotNull { key -> activeGroups.firstOrNull { it.key == key } } else activeGroups
    val displayOrders = orderedGroups.map(::groupRepresentative)
    // While editing, numbers must follow the draft immediately so changing STT is visible in the list/map.
    // Outside edit mode, keep the persisted stable route numbers.
    val displayedRouteNumbers = if (editing) {
        orderedGroups.flatMapIndexed { index, group ->
            group.orders.map { it.code to (index + 1) }
        }.toMap()
    } else {
        stableRouteNumbers
    }

    BaseMapScreen(
        active = active,
        orders = displayOrders,
        anchorPoint = vm.routeAnchorPoint(),
        routeNumberingEnabled = vm.routeNumberingEnabled,
        stableRouteNumbers = displayedRouteNumbers,
        editingStt = editing,
        focusOrderCode = focusOrderCode,
        onFocusConsumed = onFocusConsumed,
        onOpenOrder = onOpenOrder,
        onCreateRoute = {
            if (streetRouteJob?.isActive != true) {
                val ordersSnapshot = vm.orders.toList()
                val customersSnapshot = vm.customers.toList()
                val numberingSnapshot = vm.routeNumberingEnabled
                val sttSnapshot = ordersSnapshot.associate { it.code to vm.routeSttFor(it.code) }
                val anchorSnapshot = vm.routeAnchorPoint()
                val groupsSnapshot = activeGroups.toList()
                val samples = vm.streetLearningSamples()
                val history = vm.streetLearningHistory()
                val legacy = vm.legacyRouteLearningSnapshot()
                streetRouteJob = streetRouteScope.launch {
                    Toast.makeText(context, "Đang tạo tuyến…", Toast.LENGTH_SHORT).show()
                    val sortedGroups = withContext(Dispatchers.Default) {
                        val located = groupsSnapshot.filter { deliveryGroupPoint(it) != null }
                        val pending = groupsSnapshot.filter { deliveryGroupPoint(it) == null }
                        fun point(p: MapPoint) = StreetRouteLearning.Point(p.latitude, p.longitude)
                        val origin = point(anchorSnapshot ?: located.firstOrNull()?.let(::deliveryGroupPoint) ?: DEFAULT_MAP_POINT)
                        val stops = located.map { group ->
                            val p = deliveryGroupPoint(group)!!
                            val c = group.customer
                            val cp = c?.let { pointFromStrings(it.latitude, it.longitude) }
                            // A different delivery address must not borrow the customer's primary street.
                            val street = if (cp != null && straightDistanceMeters(cp, p) <= 50.0) c?.streetName.orEmpty() else ""
                            StreetRouteLearning.Stop(group.key, street, point(p))
                        }
                        fun cell(p: StreetRouteLearning.Point) =
                            "${kotlin.math.floor(p.lat * 500.0).toInt()},${kotlin.math.floor(p.lng * 500.0).toInt()}"
                        val oldWeight: (StreetRouteLearning.Point, StreetRouteLearning.Point) -> Double = { a, b ->
                            -(legacy["${cell(a)}>${cell(b)}"] ?: 0.0) * 60.0
                        }
                        val keys = try {
                            StreetRouteLearning.Model(samples, history).order(stops, origin, oldWeight)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            // Learning failures leave the established geographic/legacy planner available.
                            StreetRouteLearning.Model(emptyList(), emptyList()).order(stops, origin, oldWeight)
                        }
                        val byKey = located.associateBy { it.key }
                        keys.mapNotNull(byKey::get) + pending
                    }
                    // Do not apply a stale plan if delivery/customer/route data changed while calculating.
                    if (vm.orders.toList() != ordersSnapshot || vm.customers.toList() != customersSnapshot ||
                        vm.routeNumberingEnabled != numberingSnapshot || vm.routeAnchorPoint() != anchorSnapshot ||
                        ordersSnapshot.any { vm.routeSttFor(it.code) != sttSnapshot[it.code] } || editing) {
                        Toast.makeText(context, "Dữ liệu đã thay đổi. Hãy bấm Tạo tuyến lại.", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
                        vm.replaceRouteStt(sortedGroups.map { it.orders.map(Order::code) })
                        vm.enableRouteNumbering()
                        Toast.makeText(context, "Đã tạo tuyến theo ${sortedGroups.size} điểm giao", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onEditRoute = {
            streetRouteJob?.cancel()
            draft = activeGroups.map { it.key }
            editing = true
            Toast.makeText(context, "Chạm bong bóng để focus đơn trong danh sách, rồi sửa STT tại danh sách", Toast.LENGTH_SHORT).show()
        },
        onClearRoute = {
            streetRouteJob?.cancel()
            vm.clearRouteNumbering()
            editing = false
            editMarker = null
            editNumberText = ""
            Toast.makeText(context, "Đã xóa toàn bộ STT", Toast.LENGTH_SHORT).show()
        },
        onSaveRoute = {
            val confirmedByKey = activeGroups.associateBy { it.key }
            val savedGroups = draft.mapNotNull(confirmedByKey::get)
            // Draft edits are intentionally cheap; persist the final order only once when Save is pressed.
            vm.reorderOrders(savedGroups.flatMap { it.orders.map(Order::code) })
            vm.replaceRouteStt(savedGroups.map { it.orders.map(Order::code) })
            vm.learnStreetRoute(savedGroups.mapNotNull { it.orders.firstOrNull()?.code })
            val learnedPoints = savedGroups.mapNotNull(::deliveryGroupPoint)
            vm.learnRoutePattern(learnedPoints)
            editMarker = null
            editNumberText = ""
            editing = false
        },
        onEditStt = { marker ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
            }
        },
        editingCode = editMarker?.order?.code,
        editingNumberText = editNumberText,
        onEditingNumberChange = { editNumberText = it.filter(Char::isDigit).take(4) },
        onCommitEdit = { commitGroupedDirectStt() },
        onCancelEdit = {
            editMarker = null
            editNumberText = ""
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },
        onImportStt = { importLauncher.launch("text/*") }
    )


    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave=false },
        title = { Text("Lưu STT tuyến") },
        text = { Text("Lưu thứ tự ${draft.size} điểm giao?") },
        confirmButton = { TextButton(onClick={
            val groupByKey=activeGroups.associateBy{it.key}
            val savedGroups = draft.mapNotNull(groupByKey::get)
            vm.reorderOrders(savedGroups.flatMap{it.orders.map(Order::code)})
            vm.replaceRouteStt(savedGroups.map { it.orders.map(Order::code) })
            vm.learnStreetRoute(savedGroups.mapNotNull { it.orders.firstOrNull()?.code })
            editMarker = null
            editNumberText = ""
            confirmSave=false; editing=false
        }){Text("LƯU")} },
        dismissButton = { TextButton(onClick={confirmSave=false}){Text("HỦY")} }
    )
}

@Composable
private fun BaseMapScreen(
    active: Boolean,
    orders: List<Order>,
    anchorPoint: MapPoint?,
    routeNumberingEnabled: Boolean,
    stableRouteNumbers: Map<String, Int>,
    editingStt: Boolean,
    focusOrderCode: String?,
    onFocusConsumed: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onEditStt: (MapOrderMarker) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    val context = LocalContext.current
    val currentActive by rememberUpdatedState(active)
    val driverLocation by rememberDriverLocation(active)
    var selectedOrderCode by remember { mutableStateOf<String?>(null) }
    var mapExpanded by remember { mutableStateOf(false) }

    // Snapshot the first live GPS point only for orders without a real coordinate.
    // Later GPS updates move only the driver marker; they must not rebuild every order marker.
    var pendingOrdersGpsAnchor by remember { mutableStateOf<MapPoint?>(null) }
    LaunchedEffect(driverLocation) {
        if (pendingOrdersGpsAnchor == null && driverLocation != null) {
            pendingOrdersGpsAnchor = driverLocation
        }
    }
    val pendingOrdersPoint = anchorPoint ?: pendingOrdersGpsAnchor ?: DEFAULT_MAP_POINT

    // groupRepresentative already carries coordinates from the matched customer.
    // Resolving a second customer by name/address here could move the pin to somebody else.
    val mappedOrders = remember(orders, pendingOrdersPoint, routeNumberingEnabled, stableRouteNumbers) {
        buildMapOrderMarkers(orders, pendingOrdersPoint, routeNumberingEnabled, stableRouteNumbers)
    }
    val selectedMarker = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }

    LaunchedEffect(focusOrderCode, mappedOrders) {
        val code = focusOrderCode ?: return@LaunchedEffect
        if (mappedOrders.any { it.order.code == code }) {
            mapExpanded = false
            selectedOrderCode = code
        }
        onFocusConsumed()
    }

    // Keep map/list state composed while changing tabs, but do not place hidden
    // content: unplaced children cannot receive touches through an overlay tab.
    Column(Modifier.fillMaxSize().background(Background).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (active) placeable.placeRelative(0, 0)
        }
    }) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GoongOrderMap(
                modifier = Modifier.fillMaxSize(),
                active = active,
                orders = mappedOrders,
                driverLocation = driverLocation,
                selectedOrderNumber = if (editingStt) null else selectedMarker?.number,
                expanded = mapExpanded,
                editingStt = editingStt,
                onToggleExpand = { mapExpanded = !mapExpanded },
                onOrderSelected = { marker ->
                    if (currentActive) {
                        selectedOrderCode = marker.order.code
                        if (editingStt) onEditStt(marker)
                    }
                }
            )

            if (!mapExpanded) {
                MapOrderBottomSheet(
                    orders = mappedOrders,
                    selectedNumber = selectedMarker?.number,
                    selectedOrderCode = selectedOrderCode,
                    editingStt = editingStt,
                    onOrderClick = { marker -> if (currentActive && !editingStt) onOpenOrder(marker.order.code) },
                    onNumberClick = { marker ->
                        if (currentActive) {
                            selectedOrderCode = marker.order.code
                            if (editingStt) onEditStt(marker)
                        }
                    },
                    editingCode = editingCode,
                    editingNumberText = editingNumberText,
                    onEditingNumberChange = onEditingNumberChange,
                    onCommitEdit = onCommitEdit,
                    onCancelEdit = onCancelEdit,
                    onNavigate = { marker ->
                        if (currentActive) {
                            if (marker.hasRealCoordinate) {
                                openGoogleNavigation(context, marker.point)
                            } else {
                                Toast.makeText(context, "Đơn này chưa có tọa độ. Hãy bổ sung trong Khách hàng.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCreateRoute = onCreateRoute,
                    onEditRoute = onEditRoute,
                    onClearRoute = onClearRoute,
                    onSaveRoute = onSaveRoute,
                    onExportStt = onExportStt,
                    onImportStt = onImportStt
                )
            }
        }
    }
}

// Bottom sheet dạng danh sách đơn hàng giống hình tham chiếu: số thứ tự, nút dẫn đường, mã đơn, tên khách và số tiền.
@Composable
private fun BoxScope.MapOrderBottomSheet(
    orders: List<MapOrderMarker>,
    selectedNumber: Int?,
    selectedOrderCode: String?,
    editingStt: Boolean,
    onOrderClick: (MapOrderMarker) -> Unit,
    onNumberClick: (MapOrderMarker) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onNavigate: (MapOrderMarker) -> Unit,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    var routeMenuExpanded by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedOrderCode, selectedNumber, orders) {
        val i = selectedOrderCode
            ?.let { code -> orders.indexOfFirst { it.order.code == code } }
            ?.takeIf { it >= 0 }
            ?: selectedNumber?.let { n -> orders.indexOfFirst { it.number == n } }?.takeIf { it >= 0 }
            ?: -1
        if (i >= 0) listState.scrollToItem(i)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        color = Color.White.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 185.dp, max = 250.dp).padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(3.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xFF9BA8B8))
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, null, tint = Orange, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (editingStt) "Sửa STT • ${orders.size} đơn" else "${orders.size} đơn hàng",
                    color = Navy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { if (editingStt) onSaveRoute() else routeMenuExpanded = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (editingStt) Icons.Default.Save else Icons.Default.KeyboardArrowUp,
                            if (editingStt) "Lưu STT" else "Công cụ tuyến",
                            tint = Orange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    if (!editingStt) {
                        DropdownMenu(expanded = routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Tạo tuyến") },
                                leadingIcon = { Icon(Icons.Default.Route, null) },
                                onClick = { routeMenuExpanded = false; onCreateRoute() }
                            )
                            DropdownMenuItem(
                                text = { Text("Sửa STT") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { routeMenuExpanded = false; onEditRoute() }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa STT") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = { routeMenuExpanded = false; onClearRoute() }
                            )
                            DropdownMenuItem(
                                text = { Text("Xuất STT") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = { routeMenuExpanded = false; onExportStt() }
                            )
                            DropdownMenuItem(
                                text = { Text("Nhập STT") },
                                leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                                onClick = { routeMenuExpanded = false; onImportStt() }
                            )
                        }
                    }
                }
            }
            if (editingStt) {
                Text("Chạm bong bóng để focus tới đơn trong danh sách; sửa STT trực tiếp tại danh sách.", color = TextGray, fontSize = 10.sp)
            }
            Spacer(Modifier.height(if (editingStt) 4.dp else 10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(orders, key = { it.order.code }) { marker ->
                    MapOrderListRow(
                        marker = marker,
                        selected = marker.number == selectedNumber,
                        editingStt = editingStt,
                        editingCode = editingCode,
                        editingNumberText = editingNumberText,
                        inlineEditingHere = true,
                        onEditingNumberChange = onEditingNumberChange,
                        onCommitEdit = onCommitEdit,
                        onCancelEdit = onCancelEdit,
                        onClick = { onOrderClick(marker) },
                        onNumberClick = { onNumberClick(marker) },
                        onNavigate = { onNavigate(marker) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapOrderListRow(
    marker: MapOrderMarker,
    selected: Boolean,
    editingStt: Boolean,
    editingCode: String?,
    editingNumberText: String,
    inlineEditingHere: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onClick: () -> Unit,
    onNumberClick: () -> Unit,
    onNavigate: () -> Unit
) {
    val order = marker.order
    Card(
        modifier = Modifier.fillMaxWidth().height(62.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) OrangeLight else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingStt && inlineEditingHere && editingCode == order.code) {
                val focusRequester = remember(order.code) { androidx.compose.ui.focus.FocusRequester() }
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                LaunchedEffect(editingCode) {
                    kotlinx.coroutines.delay(80)
                    focusRequester.requestFocus()
                }
                OutlinedTextField(
                    value = editingNumberText,
                    onValueChange = onEditingNumberChange,
                    modifier = Modifier.width(58.dp).height(52.dp).focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        onCommitEdit()
                        focusManager.clearFocus()
                    }),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
            } else {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onNumberClick() },
                    contentAlignment = Alignment.Center
                ) {
                    RouteStateCircle(if (marker.showNumber) marker.number else null, marker.hasRealCoordinate, selected || editingStt)
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (marker.hasRealCoordinate) Blue else Color(0xFFB6C0CC))
                    .clickable(enabled = marker.hasRealCoordinate) { onNavigate() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Navigation, "Dẫn đường", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(order.customer, color = TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!marker.hasRealCoordinate) Text("Chưa có tọa độ", color = OrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(order.amount, color = MoneyGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Default.ChevronRight, "Xem đơn", tint = TextGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}
