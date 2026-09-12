from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

if 'import androidx.compose.foundation.combinedClickable' not in s:
    s=s.replace('import androidx.compose.foundation.clickable\n','import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n',1)

# Add/upgrade scanner: append one MVĐ per line, reject duplicates and skip orders already in Chi tiết đơn.
needle='    var importedCount by remember { mutableIntStateOf(0) }\n    val csvImportLauncher'
replacement='''    var importedCount by remember { mutableIntStateOf(0) }
    val vtmanScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
            val currentCodes = waybills.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
            val alreadyHasInfo = vm.orders.any { it.code.trim().equals(code, ignoreCase = true) }
            when {
                alreadyHasInfo -> Toast.makeText(context, "Mã $code đã có thông tin trong Chi tiết đơn - bỏ qua", Toast.LENGTH_LONG).show()
                currentCodes.any { it.equals(code, ignoreCase = true) } -> Toast.makeText(context, "Mã $code đã được quét", Toast.LENGTH_SHORT).show()
                else -> {
                    val updatedCodes = currentCodes + code
                    waybills = updatedCodes.joinToString("\\n")
                    com.example.giaohangpro.vtman.VtmanQueueController.load(updatedCodes)
                    importedCount = 0
                    snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                    Toast.makeText(context, "Đã thêm MVĐ $code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val csvImportLauncher'''
if needle in s and 'val vtmanScanLauncher' not in s:
    s=s.replace(needle,replacement,1)

# Replace any previous scanner block between launcher declaration and csv launcher.
if 'val vtmanScanLauncher' in s:
    a=s.index('    val vtmanScanLauncher = rememberLauncherForActivityResult(ScanContract())')
    b=s.index('    val csvImportLauncher',a)
    s=s[:a]+replacement.split('    val csvImportLauncher')[0]+s[b:]

# CSV input: skip duplicate file rows and MVĐ already present in Chi tiết đơn.
old='''            val codes = text.lineSequence().map { it.trim().removePrefix("\\uFEFF") }
                .filter { it.isNotBlank() }
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter { it.isNotBlank() }.toList()
            waybills = codes.joinToString("\\n")
            com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            Toast.makeText(context, "Đã nạp ${codes.size} MVĐ từ CSV", Toast.LENGTH_SHORT).show()'''
new='''            val rawCodes = text.lineSequence().map { it.trim().removePrefix("\\uFEFF") }
                .filter { it.isNotBlank() }
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter { it.isNotBlank() }.distinctBy { it.uppercase() }.toList()
            val existingCodes = vm.orders.map { it.code.trim().uppercase() }.toSet()
            val skippedExisting = rawCodes.count { it.uppercase() in existingCodes }
            val codes = rawCodes.filterNot { it.uppercase() in existingCodes }
            waybills = codes.joinToString("\\n")
            com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
            importedCount = 0
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val suffix = if (skippedExisting > 0) " • Bỏ qua $skippedExisting mã đã có thông tin" else ""
            Toast.makeText(context, "Đã nạp ${codes.size} MVĐ từ CSV$suffix", Toast.LENGTH_LONG).show()'''
if old in s:
    s=s.replace(old,new,1)

# Import each successfully completed record immediately. If a later MVĐ fails, earlier ones remain in the order tab.
s=s.replace('''            if (records.size != importedCount) { vm.importVtmanRecords(records); importedCount = records.size }''','''            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                importedCount = records.size
            }''',1)

old_buttons='''            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { csvImportLauncher.launch("text/*") }, modifier = Modifier.weight(1f).height(42.dp)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("NẠP MVĐ", fontSize=11.sp) }
                OutlinedButton(onClick = {
                    val list = waybills.lines().flatMap { it.split(',', ';', ' ', '\\t') }.map(String::trim).filter(String::isNotBlank)
                    com.example.giaohangpro.vtman.VtmanQueueController.load(list); snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                }, modifier = Modifier.weight(1f).height(42.dp)) { Text("NẠP TEXTBOX", fontSize=11.sp) }
            }'''
new_buttons='''            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { csvImportLauncher.launch("text/*") }, modifier = Modifier.weight(1f).height(42.dp)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("NẠP MVĐ", fontSize=11.sp) }
                OutlinedButton(onClick = {
                    vtmanScanLauncher.launch(ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                        setPrompt("Quét mã vận đơn QR hoặc mã vạch")
                        setBeepEnabled(false)
                        setCaptureActivity(PortraitCaptureActivity::class.java)
                        setOrientationLocked(true)
                        setBarcodeImageEnabled(false)
                    })
                }, modifier = Modifier.weight(1f).height(42.dp)) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(4.dp)); Text("QUÉT MVĐ", fontSize=11.sp) }
            }'''
if old_buttons in s:
    s=s.replace(old_buttons,new_buttons,1)

oldtext='''                Text(
                    order.code,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )'''
newtext='''                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Text(
                    order.code,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(order.code))
                            Toast.makeText(context, "Đã copy MVĐ ${order.code}", Toast.LENGTH_SHORT).show()
                        }
                    )
                )'''
if oldtext in s:
    s=s.replace(oldtext,newtext,1)

p.write_text(s)
