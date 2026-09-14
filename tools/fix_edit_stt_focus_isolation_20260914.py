from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# 1) While editing STT, do not carry normal map selection/focus into GoongOrderMap.
old = '''                selectedOrderNumber = selectedMarker?.number,
                expanded = mapExpanded,
                editingStt = editingStt,'''
new = '''                selectedOrderNumber = if (editingStt) null else selectedMarker?.number,
                expanded = mapExpanded,
                editingStt = editingStt,'''
if old in s:
    s = s.replace(old, new, 1)
elif 'selectedOrderNumber = if (editingStt) null else selectedMarker?.number' not in s:
    raise SystemExit('GoongOrderMap selectedOrderNumber anchor not found')

# 2) Resolve a clicked marker by unique waybill code, not by editable STT number.
#    In edit mode the click must ONLY open the STT editor; normal order selection/focus is disabled.
old = '''                            readyMap.setOnMarkerClickListener { clicked ->
                                val number = clicked.title?.substringAfter("Đơn #")?.substringBefore(" ")?.toIntOrNull()
                                val marker = orders.firstOrNull { it.number == number }
                                if (marker != null) {
                                    onOrderSelected(marker)
                                    if (editingStt) onEditStt(marker)
                                    true'''
new = '''                            readyMap.setOnMarkerClickListener { clicked ->
                                val code = clicked.title?.substringAfter(" • ", "")?.trim().orEmpty()
                                val marker = orders.firstOrNull { it.order.code == code }
                                if (marker != null) {
                                    if (editingStt) onEditStt(marker) else onOrderSelected(marker)
                                    true'''
if old in s:
    s = s.replace(old, new, 1)
elif 'substringAfter(" • ", "")' not in s:
    raise SystemExit('Map marker click anchor not found')

# 3) Fully clear transient STT edit state after Save so edit-only rules cannot leak into normal mode.
old = '''            vm.reorderOrders(savedGroups.flatMap{it.orders.map(Order::code)})
            vm.replaceRouteStt(savedGroups.map { it.orders.map(Order::code) })
            confirmSave=false; editing=false'''
new = '''            vm.reorderOrders(savedGroups.flatMap{it.orders.map(Order::code)})
            vm.replaceRouteStt(savedGroups.map { it.orders.map(Order::code) })
            editMarker = null
            editNumberText = ""
            editOnMap = false
            confirmSave=false; editing=false'''
if old in s:
    s = s.replace(old, new, 1)
elif 'editMarker = null\n            editNumberText = ""\n            editOnMap = false\n            confirmSave=false; editing=false' not in s:
    raise SystemExit('Save STT cleanup anchor not found')

p.write_text(s)
print('STT edit mode isolated from normal map focus and marker identity now uses order code')
