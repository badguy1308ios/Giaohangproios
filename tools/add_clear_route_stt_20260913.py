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

# Wire clear callback in MapScreen -> BaseMapScreen. Keep all existing route/edit behavior intact.
map_start=s.find('fun MapScreen(')
map_end=s.find('\n@Composable\nprivate fun BaseMapScreen(', map_start)
if map_start < 0 or map_end < 0:
    raise SystemExit('MapScreen/BaseMapScreen anchors not found')
map_chunk=s[map_start:map_end]
if 'vm.clearRouteNumbering()' not in map_chunk:
    anchor='''        onSaveRoute = {
'''
    clear='''        onClearRoute = {
            vm.clearRouteNumbering()
            editing = false
            editMarker = null
            editNumberText = ""
            editOnMap = false
            Toast.makeText(context, "Đã xóa toàn bộ STT", Toast.LENGTH_SHORT).show()
        },
'''
    if anchor not in map_chunk:
        raise SystemExit('MapScreen onSaveRoute anchor not found')
    map_chunk=map_chunk.replace(anchor,clear+anchor,1)
    s=s[:map_start]+map_chunk+s[map_end:]

# Ensure BaseMapScreen signature has callback.
base_start=s.find('private fun BaseMapScreen(')
base_body=s.find(') {', base_start)
base_sig=s[base_start:base_body]
if 'onClearRoute: () -> Unit' not in base_sig:
    base_sig=base_sig.replace('    onEditRoute: () -> Unit,\n','    onEditRoute: () -> Unit,\n    onClearRoute: () -> Unit,\n',1)
    s=s[:base_start]+base_sig+s[base_body:]

# Pass callback from BaseMapScreen to MapOrderBottomSheet.
base_start=s.find('private fun BaseMapScreen(')
base_end=s.find('\n// Bottom sheet', base_start)
base_chunk=s[base_start:base_end]
if '                    onClearRoute = onClearRoute,' not in base_chunk:
    anchor='''                    onEditRoute = onEditRoute,
                    onSaveRoute = onSaveRoute,
'''
    repl='''                    onEditRoute = onEditRoute,
                    onClearRoute = onClearRoute,
                    onSaveRoute = onSaveRoute,
'''
    if anchor not in base_chunk:
        raise SystemExit('MapOrderBottomSheet call anchor not found')
    base_chunk=base_chunk.replace(anchor,repl,1)
    s=s[:base_start]+base_chunk+s[base_end:]

# Ensure MapOrderBottomSheet accepts callback.
sheet_start=s.find('private fun BoxScope.MapOrderBottomSheet(')
sheet_body=s.find(') {', sheet_start)
sheet_sig=s[sheet_start:sheet_body]
if 'onClearRoute: () -> Unit' not in sheet_sig:
    sheet_sig=sheet_sig.replace('    onEditRoute: () -> Unit,\n','    onEditRoute: () -> Unit,\n    onClearRoute: () -> Unit,\n',1)
    s=s[:sheet_start]+sheet_sig+s[sheet_body:]

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
print('clear STT wiring fixed: detail/map/map-list share routeNumberingEnabled=false')
