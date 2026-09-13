from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# Replace MapScreen + BaseMapScreen, keeping the bottom-sheet section marker.
start = s.index('@Composable\nfun MapScreen(vm: MainViewModel)')
end = s.index('// Bottom sheet dạng danh sách đơn hàng', start)
new_map = r'''@Composable
fun MapScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(vm.orders.map { it.code }) }
    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("STT,MVĐ\n")
                vm.orders.forEachIndexed { i, o -> append("${i + 1},${csvCell(o.code)}\n") }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess {
            Toast.makeText(context, "Đã xuất thứ tự ${vm.orders.size} MVĐ", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Không xuất được STT", Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val codes = text.lineSequence().drop(1).mapNotNull { line ->
                line.substringAfter(',', "").trim().trim('"').takeIf(String::isNotBlank)
            }.toList()
            vm.reorderOrders(codes)
            Toast.makeText(context, "Đã nhập STT cho ${codes.size} MVĐ", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Không đọc được file STT", Toast.LENGTH_LONG).show()
        }
    }

    val displayOrders = if (editing) {
        draft.mapNotNull { code -> vm.orders.firstOrNull { it.code == code } }
    } else {
        vm.orders.toList()
    }

    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        editingStt = editing,
        onCreateRoute = {
            val sorted = vm.orders.sortedWith(
                compareBy<Order> { it.latitude.toDoubleOrNull() ?: 999.0 }
                    .thenBy { it.longitude.toDoubleOrNull() ?: 999.0 }
            ).map { it.code }
            vm.reorderOrders(sorted)
            Toast.makeText(context, "Đã tạo tuyến theo vị trí", Toast.LENGTH_SHORT).show()
        },
        onEditRoute = {
            draft = vm.orders.map { it.code }
            editing = true
            Toast.makeText(context, "Bấm STT trên bản đồ hoặc danh sách để đổi số", Toast.LENGTH_SHORT).show()
        },
        onSaveRoute = { confirmSave = true },
        onEditStt = { marker ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
            }
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },
        onImportStt = { importLauncher.launch("text/*") }
    )

    editMarker?.let { marker ->
        val maxStt = draft.size.coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { editMarker = null },
            title = { Text("Đổi STT đơn ${marker.order.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STT hiện tại: ${marker.number} • Nhập vị trí mới từ 1 đến $maxStt", color = TextGray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editNumberText,
                        onValueChange = { value -> editNumberText = value.filter(Char::isDigit).take(4) },
                        label = { Text("STT mới") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = editNumberText.toIntOrNull()?.coerceIn(1, maxStt)
                    if (target != null) {
                        val movingCode = marker.order.code
                        val current = draft.indexOf(movingCode)
                        if (current >= 0) {
                            val next = draft.toMutableList()
                            next.removeAt(current)
                            next.add((target - 1).coerceIn(0, next.size), movingCode)
                            draft = next
                        }
                        editMarker = null
                    }
                }) { Text("ÁP DỤNG") }
            },
            dismissButton = { TextButton(onClick = { editMarker = null }) { Text("HỦY") } }
        )
    }

    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave = false },
        title = { Text("Lưu STT tuyến") },
        text = { Text("Xác nhận lưu thứ tự mới? Dãy STT sẽ luôn liên tục từ 1 đến ${draft.size}.") },
        confirmButton = {
            TextButton(onClick = {
                vm.reorderOrders(draft)
                confirmSave = false
                editing = false
            }) { Text("LƯU") }
        },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("HỦY") } }
    )
}

@Composable
private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    editingStt: Boolean,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onEditStt: (MapOrderMarker) -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    val context = LocalContext.current
    val driverLocation by rememberDriverLocation()
    var selectedOrderCode by remember { mutableStateOf<String?>(null) }
    var mapExpanded by remember { mutableStateOf(false) }

    val mappedOrders = remember(orders, customers, driverLocation) {
        orders.mapIndexed { index, order ->
            val orderPoint = pointFromStrings(order.latitude, order.longitude)
            val customerPoint = customers.firstOrNull {
                it.phone.filter(Char::isDigit) == order.phone.filter(Char::isDigit) ||
                    it.name.equals(order.customer, ignoreCase = true) ||
                    it.address.equals(order.address, ignoreCase = true)
            }?.let { pointFromStrings(it.latitude, it.longitude) }
            val realPoint = orderPoint ?: customerPoint
            val displayPoint = realPoint ?: driverLocation ?: DEFAULT_MAP_POINT
            MapOrderMarker(order, displayPoint, index + 1, realPoint != null)
        }
    }
    val selectedMarker = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GoongOrderMap(
                modifier = Modifier.fillMaxSize(),
                orders = mappedOrders,
                driverLocation = driverLocation,
                selectedOrderNumber = selectedMarker?.number,
                expanded = mapExpanded,
                editingStt = editingStt,
                onToggleExpand = { mapExpanded = !mapExpanded },
                onOrderSelected = { selectedOrderCode = it.order.code },
                onEditStt = onEditStt
            )

            if (!mapExpanded) {
                MapOrderBottomSheet(
                    orders = mappedOrders,
                    selectedNumber = selectedMarker?.number,
                    editingStt = editingStt,
                    onOrderClick = { selectedOrderCode = it.order.code },
                    onNumberClick = { marker ->
                        selectedOrderCode = marker.order.code
                        if (editingStt) onEditStt(marker)
                    },
                    onNavigate = { marker ->
                        if (marker.hasRealCoordinate) {
                            openGoogleNavigation(context, marker.point)
                        } else {
                            Toast.makeText(context, "Đơn này chưa có tọa độ. Hãy bổ sung trong Khách hàng.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCreateRoute = onCreateRoute,
                    onEditRoute = onEditRoute,
                    onSaveRoute = onSaveRoute,
                    onExportStt = onExportStt,
                    onImportStt = onImportStt
                )
            }
        }
    }
}

'''
s = s[:start] + new_map + s[end:]

# Replace bottom sheet through the marker-drawable comment.
start = s.index('@Composable\nprivate fun BoxScope.MapOrderBottomSheet(')
end = s.index('// Tạo Drawable marker kiểu bong bóng', start)
new_sheet = r'''@Composable
private fun BoxScope.MapOrderBottomSheet(
    orders: List<MapOrderMarker>,
    selectedNumber: Int?,
    editingStt: Boolean,
    onOrderClick: (MapOrderMarker) -> Unit,
    onNumberClick: (MapOrderMarker) -> Unit,
    onNavigate: (MapOrderMarker) -> Unit,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    var routeMenuExpanded by remember { mutableStateOf(false) }
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
                Text("Chạm vào vòng tròn STT hoặc bong bóng trên bản đồ để đổi vị trí.", color = TextGray, fontSize = 10.sp)
            }
            Spacer(Modifier.height(if (editingStt) 4.dp else 10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(orders, key = { it.order.code }) { marker ->
                    MapOrderListRow(
                        marker = marker,
                        selected = marker.number == selectedNumber,
                        editingStt = editingStt,
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
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onNumberClick() },
                contentAlignment = Alignment.Center
            ) {
                NumberCircle(marker.number, selected = selected || editingStt)
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

'''
s = s[:start] + new_sheet + s[end:]

# Replace GoongOrderMap signature/body up to MapControlButton.
start = s.index('@Composable\nprivate fun GoongOrderMap(')
end = s.index('@Composable\nprivate fun MapControlButton(', start)
new_goong = r'''@Composable
private fun GoongOrderMap(
    modifier: Modifier,
    orders: List<MapOrderMarker>,
    driverLocation: MapPoint?,
    selectedOrderNumber: Int?,
    expanded: Boolean,
    editingStt: Boolean,
    onToggleExpand: () -> Unit,
    onOrderSelected: (MapOrderMarker) -> Unit,
    onEditStt: (MapOrderMarker) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialDriverFocus by remember { mutableStateOf(false) }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { readyMap ->
                        readyMap.setTileCacheEnabled(true)
                        readyMap.setStyle(Style.Builder().fromJson(goongStyle(context))) {
                            map = readyMap
                            readyMap.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(DEFAULT_MAP_POINT.latitude, DEFAULT_MAP_POINT.longitude))
                                .zoom(14.0).build()
                            readyMap.setOnMarkerClickListener { clicked ->
                                val number = clicked.title?.substringAfter("Đơn #")?.substringBefore(" ")?.toIntOrNull()
                                val marker = orders.firstOrNull { it.number == number }
                                if (marker != null) {
                                    onOrderSelected(marker)
                                    if (editingStt) onEditStt(marker)
                                    true
                                } else false
                            }
                        }
                    }
                }
            },
            update = { view -> view.getMapAsync { readyMap -> if (readyMap.style != null) map = readyMap } }
        )

        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapControlButton(Icons.Default.MyLocation, "Vị trí của tôi") {
                driverLocation?.let { point -> map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0)) }
            }
            MapControlButton(Icons.Default.Add, "Phóng to") { map?.animateCamera(CameraUpdateFactory.zoomBy(1.0)) }
            MapControlButton(Icons.Default.Remove, "Thu nhỏ") { map?.animateCamera(CameraUpdateFactory.zoomBy(-1.0)) }
            MapControlButton(if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Mở rộng bản đồ") { onToggleExpand() }
        }
    }

    LaunchedEffect(map, driverLocation) {
        val readyMap = map
        val point = driverLocation
        if (!didInitialDriverFocus && readyMap != null && point != null) {
            readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
            didInitialDriverFocus = true
        }
    }

    LaunchedEffect(map, orders, driverLocation, selectedOrderNumber, editingStt) {
        map?.let { readyMap ->
            readyMap.clear()
            driverLocation?.let { point ->
                val driverIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(createDriverMotorbikeBitmap(context))
                readyMap.addMarker(
                    MarkerOptions().position(LatLng(point.latitude, point.longitude)).icon(driverIcon)
                        .title("🛵 Vị trí hiện tại của tài xế").snippet("GPS đang cập nhật")
                )
            }

            // Vẽ marker đang chọn cuối cùng để luôn nằm trên các bong bóng gần kề.
            val drawOrders = if (selectedOrderNumber == null) orders else orders.sortedBy { it.number == selectedOrderNumber }
            drawOrders.forEach { markerData ->
                val order = markerData.order
                val numberBitmap = (createNumberBubbleDrawable(context, markerData.number, !markerData.hasRealCoordinate) as android.graphics.drawable.BitmapDrawable).bitmap
                val numberIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(numberBitmap)
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(markerData.point.latitude, markerData.point.longitude))
                        .icon(numberIcon)
                        .title("Đơn #${markerData.number} • ${order.code}")
                        .snippet(if (editingStt) "Chạm để đổi STT" else if (markerData.hasRealCoordinate) order.address else "Chưa có tọa độ giao hàng")
                )
            }

            selectedOrderNumber?.let { number ->
                orders.firstOrNull { it.number == number }?.let { selected ->
                    readyMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(selected.point.latitude, selected.point.longitude),
                            if (selected.hasRealCoordinate) 16.0 else 15.0
                        )
                    )
                }
            }
        }
    }
}

'''
s = s[:start] + new_goong + s[end:]

p.write_text(s)
print('map STT direct edit/focus patch applied')
