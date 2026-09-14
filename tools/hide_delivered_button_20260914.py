from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()
old = 'ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)'
new = 'if (!delivered) { ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered) }'
s = s.replace(old, new)
p.write_text(s)

cleanup_patch = Path('tools/remove_customer_card_thumbnails_20260914.py')
if cleanup_patch.exists():
    exec(compile(cleanup_patch.read_text(), str(cleanup_patch), 'exec'))
