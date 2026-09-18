from pathlib import Path
import re
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
# The app already has the protected ensureCustomerFromImportedOrder() implementation.
# Remove the temporary duplicate helper injected by the VTMan patch while keeping importVtmanRecords().
s=re.sub(r'''\n    private fun normalizePhone\(raw: String\): String \{.*?\n    fun importVtmanRecords\(''', '\n    fun importVtmanRecords(', s, count=1, flags=re.S)
p.write_text(s)
