from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

start = s.index('@Composable\nprivate fun VtmanExportScreen(')
end = s.index('\n@Composable\nprivate fun SettingsSection', start)

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

    val vtmanScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf(String::isNotEmpty)?.let { code ->
            val codes = currentCodes()
            val alreadyHasInfo = vm.orders.any { it.code.trim().equals(code, ignoreCase = true) }
            when {
                alreadyHasInfo -> Toast.makeText(context, "Mã $code đã có thông tin trong Chi tiết đơn - bỏ qua", Toast.LENGTH_LONG).show()
                codes.any { it.equals(code, ignoreCase = true) } -> Toast.makeText(context, "Mã $code đã được quét", Toast.LENGTH_SHORT).show()
                else -> {
                    loadQueue(codes + code)
                    Toast.makeText(context, "Đã thêm MVĐ $code", Toast.LENGTH_SHORT).show()
                }
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

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(Orange).padding(horizontal = 6.dp),
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
                            vtmanScanLauncher.launch(ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")
                                setBeepEnabled(false)
                                setCaptureActivity(PortraitCaptureActivity::class.java)
                                setOrientationLocked(true)
                                setBarcodeImageEnabled(false)
                            })
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
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
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("BẬT POPUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.AccessibilityNew, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("TRỢ NĂNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Danh sách mã vận đơn", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(12.dp), color = OrangeLight) {
                            Text("$codeCount mã", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = OrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    OutlinedTextField(
                        value = waybills,
                        onValueChange = { waybills = it },
                        modifier = Modifier.fillMaxWidth().height(277.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Navy),
                        placeholder = { Text("Mỗi dòng 1 mã vận đơn", fontSize = 10.sp) },
                        singleLine = false,
                        maxLines = Int.MAX_VALUE,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text("Mỗi dòng 1 mã vận đơn", color = TextGray, fontSize = 9.sp, modifier = Modifier.padding(start = 3.dp, top = 3.dp))
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
                        Text("Đang xử lý: ${snapshot.currentWaybill}", fontSize = 11.sp, color = OrangeDark)
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
                    Text("💡 Hướng dẫn nhanh", color = OrangeDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("• QUÉT MVĐ: quét QR hoặc mã vạch liên tục.", color = Navy, fontSize = 10.sp)
                    Text("• NẠP MVĐ: lấy danh sách từ file.", color = Navy, fontSize = 10.sp)
                    Text("• Danh sách bên phải giữ nguyên chiều cao và tự cuộn khi có nhiều mã.", color = Navy, fontSize = 10.sp)
                }
            }
        }
    }
}
'''

s = s[:start] + screen + s[end:]
p.write_text(s)
