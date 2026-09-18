from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

old_state = '''    var deleteMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }'''
new_state = '''    var deleteMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleteAllWasChosen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }'''
if old_state in s:
    s = s.replace(old_state, new_state, 1)
elif 'var deleteAllWasChosen by remember' not in s:
    raise SystemExit('delete selection state anchor not found')

old_all = '''                        checked = allVisibleSelected,
                        onCheckedChange = { all -> selected = if (all) filteredOrders.map { it.code }.toSet() else emptySet() }
                    )'''
new_all = '''                        checked = allVisibleSelected,
                        onCheckedChange = { all ->
                            deleteAllWasChosen = all
                            selected = if (all) filteredOrders.map { it.code }.toSet() else emptySet()
                        }
                    )'''
if old_all in s:
    s = s.replace(old_all, new_all, 1)
elif 'deleteAllWasChosen = all' not in s:
    raise SystemExit('select-all checkbox anchor not found')

old_individual = '''                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + order.code else selected - order.code
                                    }'''
new_individual = '''                                    onCheckedChange = { checked ->
                                        deleteAllWasChosen = false
                                        selected = if (checked) selected + order.code else selected - order.code
                                    }'''
if old_individual in s:
    s = s.replace(old_individual, new_individual, 1)
elif 'deleteAllWasChosen = false\n                                        selected = if (checked)' not in s:
    raise SystemExit('individual checkbox anchor not found')

old_enter = '''                onClick = { showTools = false; selected = emptySet(); deleteMode = true }
            )'''
new_enter = '''                onClick = {
                    showTools = false
                    selected = emptySet()
                    deleteAllWasChosen = false
                    deleteMode = true
                }
            )'''
if old_enter in s:
    s = s.replace(old_enter, new_enter, 1)
elif 'deleteAllWasChosen = false\n                    deleteMode = true' not in s:
    raise SystemExit('delete mode entry anchor not found')

old_cancel = '''                    TextButton(onClick = { deleteMode = false; selected = emptySet() }) { Text("HỦY") }'''
new_cancel = '''                    TextButton(onClick = {
                        deleteMode = false
                        selected = emptySet()
                        deleteAllWasChosen = false
                    }) { Text("HỦY") }'''
if old_cancel in s:
    s = s.replace(old_cancel, new_cancel, 1)
elif 'deleteAllWasChosen = false\n                    }) { Text("HỦY") }' not in s:
    raise SystemExit('delete cancel anchor not found')

old_confirm = '''            TextButton(onClick = {
                vm.deleteOrders(selected)
                selected = emptySet()
                confirmDelete = false
                deleteMode = false
            }) { Text("XÓA", color = Color(0xFFE21B1B)) }'''
new_confirm = '''            TextButton(onClick = {
                val clearAllRouteMemory = deleteAllWasChosen && selected.isNotEmpty()
                vm.deleteOrders(selected)
                if (clearAllRouteMemory) vm.clearRouteNumbering()
                selected = emptySet()
                deleteAllWasChosen = false
                confirmDelete = false
                deleteMode = false
            }) { Text("XÓA", color = Color(0xFFE21B1B)) }'''
if old_confirm in s:
    s = s.replace(old_confirm, new_confirm, 1)
elif 'val clearAllRouteMemory = deleteAllWasChosen' not in s:
    raise SystemExit('delete confirm anchor not found')

p.write_text(s)
print('select-all delete now clears all persisted route STT memory')
