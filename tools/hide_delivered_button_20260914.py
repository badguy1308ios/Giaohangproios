from pathlib import Path
import re

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

action = 'ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)'
wrapped = f'if (!delivered) {{ {action} }}'

# Collapse any historical nested wrappers back to exactly one wrapper.
pattern = re.compile(r'(?:if \(!delivered\) \{\s*)+' + re.escape(action) + r'(?:\s*\})+')
s = pattern.sub(wrapped, s)
# If a bare button ever appears, wrap it once.
s = s.replace(action, wrapped)
# The previous replacement may match inside the wrapper we just created; collapse once more.
s = pattern.sub(wrapped, s)

p.write_text(s)

cleanup_patch = Path('tools/remove_customer_card_thumbnails_20260914.py')
if cleanup_patch.exists():
    exec(compile(cleanup_patch.read_text(), str(cleanup_patch), 'exec'))
