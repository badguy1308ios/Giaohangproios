from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

helper = '''\n@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n@Composable\nprivate fun CopyableWaybillCode(\n    code: String,\n    modifier: Modifier = Modifier\n) {\n    val context = LocalContext.current\n    Text(\n        text = code,\n        color = Navy,\n        fontSize = 15.sp,\n        fontWeight = FontWeight.ExtraBold,\n        modifier = modifier.combinedClickable(\n            onClick = {},\n            onLongClick = {\n                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager\n                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(\"MVĐ\", code))\n                Toast.makeText(context, \"Đã copy MVĐ $code\", Toast.LENGTH_SHORT).show()\n            }\n        )\n    )\n}\n'''

anchor = '\n@Composable\nprivate fun GroupedOrderDetail('
if 'private fun CopyableWaybillCode(' not in s:
    i = s.find(anchor)
    if i < 0:
        raise SystemExit('GroupedOrderDetail anchor not found')
    s = s[:i] + helper + s[i:]

old = 'Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))'
new = 'CopyableWaybillCode(order.code, Modifier.weight(1f))'
count = s.count(old)
if count:
    s = s.replace(old, new)

p.write_text(s)
print(f'long-press MVĐ copy restored; replaced {count} order-code labels')
