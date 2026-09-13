from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Shared persisted route state: clearing disables numbering everywhere without touching order data/order sequence.
needle='''    fun enableRouteNumbering() {
        routeNumberingEnabled = true
        prefs.edit().putBoolean("route_numbering_enabled_v1", true).apply()
    }
'''
replacement=needle+'''\n    fun clearRouteNumbering() {
        routeNumberingEnabled = false
        prefs.edit().putBoolean("route_numbering_enabled_v1", false).apply()
    }
'''
if 'fun clearRouteNumbering()' not in s:
    if needle not in s: raise SystemExit('enableRouteNumbering anchor not found')
    s=s.replace(needle,replacement,1)

# Add callback through map screen.
s=s.replace('''        onEditRoute = { editing = true },
        onSaveRoute = {''','''        onEditRoute = { editing = true },
        onClearRoute = {
            vm.clearRouteNumbering()
            editing = false
            editMarker = null
            editNumberText = ""
            Toast.makeText(context, "Đã xóa toàn bộ STT", Toast.LENGTH_SHORT).show()
        },
        onSaveRoute = {''',1)

s=s.replace('''    onEditRoute: () -> Unit,
    onSaveRoute: () -> Unit,''','''    onEditRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveRoute: () -> Unit,''',1)

# Existing route pencil/up-arrow menu: add Xóa STT immediately after Sửa STT.
menu='''                            DropdownMenuItem(
                                text = { Text("Sửa STT") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { routeMenuExpanded = false; onEditRoute() }
                            )'''
menu_new=menu+'''\n                            DropdownMenuItem(
                                text = { Text("Xóa STT") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = { routeMenuExpanded = false; onClearRoute() }
                            )'''
if 'text = { Text("Xóa STT") }' not in s:
    if menu not in s: raise SystemExit('route menu anchor not found')
    s=s.replace(menu,menu_new,1)

p.write_text(s)
print('clear STT added: shared routeNumberingEnabled=false drives detail circles, map bubbles and map list blank')
