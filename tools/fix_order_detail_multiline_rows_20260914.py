from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

old = '''    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            color = Navy,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
'''
new = '''    Row(
        rowModifier,
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = Navy,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            softWrap = true
        )
    }
'''
if old in s:
    s = s.replace(old, new, 1)
    p.write_text(s)
    print('order detail info rows now wrap naturally and show full content')
elif new in s:
    print('order detail info rows already support multiline text')
else:
    raise SystemExit('OrderInfoRow multiline anchor not found')
