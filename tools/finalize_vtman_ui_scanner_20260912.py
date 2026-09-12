from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

start = s.index('@Composable\nprivate fun VtmanExportScreen(')
end = s.index('\n@Composable\nprivate fun SettingsSection', start)

helper = '''\nprivate fun csvCell(v: String): String = "\\\"" + v.replace("\\\"", "\\\"\\\"") + "\\\""\n'''

screen = r'''@Composable
private fun VtmanExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var waybills by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(com.example.giaohangpro.vtman.VtmanQueueController.snapshot()) }
    var importedCount by remember { mutableIntStateOf(0) }

    fun currentCodes(): List<String> = waybills.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.uppercase() }
        .toList()

    fun loadQueue(codes: List<String>) {
        waybills = codes.joinToString("\n")
        com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
        importedCount = 0
        snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
    }

    val continuousScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanned = result.data
                ?.getStringArrayListExtra(ContinuousVtmanScanActivity.EXTRA_NEW_CODES)
                .orEmpty()
            if (scanned.isNotEmpty()) {
                val merged = (currentCodes() + scanned).distinctBy { it.uppercase() }
                loadQueue(merged)
                Toast.makeText(context, "Đã thêm ${scanned.size} MVĐ từ camera", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val imported = text.lineSequence()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter(String::isNotBlank)
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter(String::isNotBlank)
                .distinctBy { it.uppercase() }
                .toList()
            val existing = vm.orders.map { it.code.trim().uppercase() }.toSet()
            val newCodes = imported.filterNot { it.uppercase() in existing }
            loadQueue(newCodes)
            val skipped = imported.size - newCodes.size
            Toast.makeText(context, "Đã nạp ${newCodes.size} MVĐ" + if (skipped > 0) " · bỏ qua $skipped mã đã có" else "", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không đọc được file danh sách", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val records = com.example.giaohangpro.vtman.VtmanQueueController.records()
            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                importedCount = records.size
            }
            kotlinx.coroutines.delay(400)
        }
    }

    val codeCount = currentCodes().size
    val softOrange = Color(0xFFC95C22)
    val softOrangeDark = Color(0xFFB84F1B)

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(softOrange).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White)
                }
                Text("VTMan Export", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 6.dp, vertical = 6.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, ContinuousVtmanScanActivity::class.java).apply {
                                putStringArrayListExtra(ContinuousVtmanScanActivity.EXTRA_TEXTBOX_CODES, ArrayList(currentCodes()))
                                putStringArrayListExtra(ContinuousVtmanScanActivity.EXTRA_ORDER_CODES, ArrayList(vm.orders.map { it.code }))
                            }
                            continuousScanLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("QUÉT MVĐ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("QR / MÃ VẠCH", fontSize = 9.sp)
                        }
                    }

                    Button(
                        onClick = { csvImportLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("NẠP MVĐ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Từ file / danh sách", fontSize = 9.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
                            } else {
                                context.startService(android.content.Intent(context, com.example.giaohangpro.vtman.VtmanOverlayService::class.java))
                                Toast.makeText(context, "Mở VTMan > Gạch phát offline, rồi bấm Chạy trên popup", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("BẬT POPUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.AccessibilityNew, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("TRỢ NĂNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(277.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Column(Modifier.fillMaxSize().padding(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Danh sách MVĐ", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(12.dp), color = OrangeLight) {
                                Text("$codeCount mã", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = softOrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    waybills = ""
                                    com.example.giaohangpro.vtman.VtmanQueueController.load(emptyList())
                                    importedCount = 0
                                    snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                                    Toast.makeText(context, "Đã xóa toàn bộ MVĐ trong textbox", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD33B32))
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("XÓA", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = waybills,
                            onValueChange = { waybills = it },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Navy),
                            placeholder = { Text("Mỗi dòng 1 mã vận đơn", fontSize = 10.sp) },
                            singleLine = false,
                            maxLines = Int.MAX_VALUE,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Tiến độ: ${snapshot.processed}/${snapshot.total} • Lấy được: ${snapshot.written} • Bỏ qua: ${snapshot.skipped}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(snapshot.status, fontSize = 11.sp, color = if (snapshot.error.isBlank()) TextGray else Color(0xFFE21B1B))
                    if (snapshot.currentWaybill.isNotBlank()) {
                        Text("Đang xử lý: ${snapshot.currentWaybill}", fontSize = 11.sp, color = softOrangeDark)
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E6)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD29A))
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("💡 Hướng dẫn nhanh", color = softOrangeDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("• QUÉT MVĐ: camera quét liên tục QR / mã vạch.", color = Navy, fontSize = 10.sp)
                    Text("• Mã mới tự thêm; mã trùng sẽ hiện Toast.", color = Navy, fontSize = 10.sp)
                    Text("• XÓA: xóa toàn bộ mã trong textbox và hàng chờ.", color = Navy, fontSize = 10.sp)
                    Text("• Camera tự thoát sau 30 giây không quét được mã.", color = Navy, fontSize = 10.sp)
                }
            }
        }
    }
}
'''

# Remove any old csvCell helper adjacent to the VTMan screen so only one survives.
tail = s[end:]
while tail.startswith('\nprivate fun csvCell'):
    next_marker = tail.find('\n@Composable\nprivate fun SettingsSection')
    if next_marker >= 0:
        tail = tail[next_marker:]
        break

s = s[:start] + screen + helper + tail
p.write_text(s)
