from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

if 'import androidx.compose.foundation.combinedClickable' not in s:
    s=s.replace('import androidx.compose.foundation.clickable\n','import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n',1)

# Clean accidental duplicates from prior patch runs.
s=s.replace('''    var importedCount by remember { mutableIntStateOf(0) }
    var importedCount by remember { mutableIntStateOf(0) }
''','''    var importedCount by remember { mutableIntStateOf(0) }
''')

# Add/upgrade scanner only once.
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

# Ensure OrderCard has context and opt-in for combinedClickable.
base='''@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {
    Card('''
repl='''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {
    val context = LocalContext.current
    Card('''
if base in s:
    s=s.replace(base,repl,1)

s=s.replace('''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable''','''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable''')

# Incremental import: only new completed records are appended to Chi tiết đơn.
s=s.replace('''            if (records.size != importedCount) { vm.importVtmanRecords(records); importedCount = records.size }''','''            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                importedCount = records.size
            }''',1)

# Long-press copy if still missing.
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
