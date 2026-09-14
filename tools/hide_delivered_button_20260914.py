from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()
old = 'ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)'
new = 'if (!delivered) { ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered) }'
s = s.replace(old, new)
p.write_text(s)

photo_patch = Path('tools/show_all_customer_gate_photos_20260914.py')
if photo_patch.exists():
    exec(compile(photo_patch.read_text(), str(photo_patch), 'exec'))
