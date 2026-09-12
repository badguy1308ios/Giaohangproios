from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

if 'import androidx.compose.foundation.combinedClickable' not in s:
    s=s.replace('import androidx.compose.foundation.clickable\n','import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n',1)

needle='    var importedCount by remember { mutableIntStateOf(0) }\n    val csvImportLauncher'
replacement='''    var importedCount by remember { mutableIntStateOf(0) }
    val vtmanScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
            waybills = code
            com.example.giaohangpro.vtman.VtmanQueueController.load(listOf(code))
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            Toast.makeText(context, "Đã nạp MVĐ $code", Toast.LENGTH_SHORT).show()
        }
    }
    val csvImportLauncher'''
if needle in s and 'val vtmanScanLauncher' not in s:
    s=s.replace(needle,replacement,1)

old='''            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { csvImportLauncher.launch("text/*") }, modifier = Modifier.weight(1f).height(42.dp)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("NẠP MVĐ", fontSize=11.sp) }
                OutlinedButton(onClick = {
                    val list = waybills.lines().flatMap { it.split(',', ';', ' ', '\\t') }.map(String::trim).filter(String::isNotBlank)
                    com.example.giaohangpro.vtman.VtmanQueueController.load(list); snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                }, modifier = Modifier.weight(1f).height(42.dp)) { Text("NẠP TEXTBOX", fontSize=11.sp) }
            }'''
new='''            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
if old in s:
    s=s.replace(old,new,1)

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
