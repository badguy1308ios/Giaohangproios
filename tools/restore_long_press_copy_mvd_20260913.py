from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

helper = r'''
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CopyableWaybillText(
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Text(
        text = code,
        color = Navy,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                Toast.makeText(context, "Đã copy MVĐ $code", Toast.LENGTH_SHORT).show()
            }
        )
    )
}
'''

if 'private fun CopyableWaybillText(' not in s:
    anchor = '@Composable\nprivate fun GroupedOrderDetail('
    if anchor not in s:
        raise SystemExit('GroupedOrderDetail anchor not found')
    s = s.replace(anchor, helper + '\n' + anchor, 1)

old = 'Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))'
new = 'CopyableWaybillText(order.code, modifier = Modifier.weight(1f))'
count = s.count(old)
if count < 2:
    raise SystemExit(f'Expected at least 2 order code headers, found {count}')
s = s.replace(old, new, 2)

p.write_text(s)
print('restored long-press MVĐ copy in grouped and single order detail cards')
