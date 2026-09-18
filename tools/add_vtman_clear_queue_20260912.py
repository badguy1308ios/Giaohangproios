from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
old='''            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                OutlinedButton(onClick = {
                    waybills = ""
                    com.example.giaohangpro.vtman.VtmanQueueController.load(emptyList())
                    snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                    importedCount = 0
                    Toast.makeText(context, "Đã xóa textbox và hàng chờ", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.width(74.dp).height(42.dp)) { Icon(Icons.Default.Clear, null); Spacer(Modifier.width(2.dp)); Text("XÓA", fontSize=10.sp) }
            }'''
if old in s:
    s=s.replace(old,new,1)
p.write_text(s)
