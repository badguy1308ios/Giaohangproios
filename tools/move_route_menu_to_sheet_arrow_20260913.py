from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Replace MapScreen wrapper so there is no floating top-right route button.
start=s.index('@Composable\nfun MapScreen(vm: MainViewModel) {')
end=s.index('\n@Composable\nprivate fun BaseMapScreen', start)
new_map=r'''@Composable
fun MapScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(vm.orders.map { it.code }) }

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

    BaseMapScreen(
        orders = vm.orders,
        customers = vm.customers,
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
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },
        onImportStt = { importLauncher.launch("text/*") }
    )

    if (editing) Dialog(onDismissRequest = { editing = false }) {
        Card(
            Modifier.fillMaxWidth().heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("SỬA TUYẾN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f, false)) {
                    itemsIndexed(draft) { index, code ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}.", Modifier.width(34.dp), fontWeight = FontWeight.Bold)
                            Text(code, Modifier.weight(1f))
                            IconButton(onClick = {
                                if (index > 0) {
                                    val m = draft.toMutableList()
                                    val x = m[index - 1]
                                    m[index - 1] = m[index]
                                    m[index] = x
                                    draft = m
                                }
                            }) { Icon(Icons.Default.KeyboardArrowUp, "Lên") }
                            IconButton(onClick = {
                                if (index < draft.lastIndex) {
                                    val m = draft.toMutableList()
                                    val x = m[index + 1]
                                    m[index + 1] = m[index]
                                    m[index] = x
                                    draft = m
                                }
                            }) { Icon(Icons.Default.KeyboardArrowDown, "Xuống") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false }) { Text("HỦY") }
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { confirmSave = true }) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(4.dp))
                        Text("LƯU")
                    }
                }
            }
        }
    }

    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave = false },
        title = { Text("Lưu tuyến") },
        text = { Text("Xác nhận lưu thứ tự tuyến hiện tại?") },
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
'''
s=s[:start]+new_map+s[end:]

# Extend BaseMapScreen with route-menu callbacks.
s=s.replace(
'''private fun BaseMapScreen(orders: List<Order>, customers: List<Customer>) {''',
'''private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {''',1)

old='''                    onNavigate = { marker ->
                        if (marker.hasRealCoordinate) {
                            openGoogleNavigation(context, marker.point)
                        } else {
                            Toast.makeText(context, "Đơn này chưa có tọa độ. Hãy bổ sung trong Khách hàng.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )'''
new='''                    onNavigate = { marker ->
                        if (marker.hasRealCoordinate) {
                            openGoogleNavigation(context, marker.point)
                        } else {
                            Toast.makeText(context, "Đơn này chưa có tọa độ. Hãy bổ sung trong Khách hàng.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCreateRoute = onCreateRoute,
                    onEditRoute = onEditRoute,
                    onExportStt = onExportStt,
                    onImportStt = onImportStt
                )'''
if old not in s:
    raise SystemExit('BaseMapScreen bottom-sheet call not found')
s=s.replace(old,new,1)

# Extend bottom-sheet parameters.
s=s.replace(
'''    onOrderClick: (MapOrderMarker) -> Unit, // Bấm dòng để focus marker trên bản đồ.
    onNavigate: (MapOrderMarker) -> Unit // Bấm biểu tượng dẫn đường để mở Google Maps.
) {''',
'''    onOrderClick: (MapOrderMarker) -> Unit, // Bấm dòng để focus marker trên bản đồ.
    onNavigate: (MapOrderMarker) -> Unit, // Bấm biểu tượng dẫn đường để mở Google Maps.
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    var routeMenuExpanded by remember { mutableStateOf(false) }''',1)

old_icon='''                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Thu gọn danh sách", tint = Orange, modifier = Modifier.size(26.dp))'''
new_icon='''                Box {
                    IconButton(
                        onClick = { routeMenuExpanded = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Công cụ tuyến",
                            tint = Orange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = routeMenuExpanded,
                        onDismissRequest = { routeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tạo tuyến") },
                            leadingIcon = { Icon(Icons.Default.Route, null) },
                            onClick = { routeMenuExpanded = false; onCreateRoute() }
                        )
                        DropdownMenuItem(
                            text = { Text("Sửa tuyến") },
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
                }'''
if old_icon not in s:
    raise SystemExit('sheet arrow icon not found')
s=s.replace(old_icon,new_icon,1)

p.write_text(s)
print('route menu moved to bottom-sheet arrow')
