from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# 1) Restore grouped direct STT editing state/commit after the grouping patch rewrites MapScreen.
old='''    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }
'''
new='''    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }
    var editOnMap by remember { mutableStateOf(false) }

    fun commitGroupedDirectStt() {
        val marker = editMarker ?: return
        val maxStt = draft.size.coerceAtLeast(1)
        val target = editNumberText.toIntOrNull()?.coerceIn(1, maxStt) ?: return
        val groupKey = activeGroups.firstOrNull { g -> g.orders.any { it.code == marker.order.code } }?.key ?: return
        val current = draft.indexOf(groupKey)
        if (current >= 0) {
            val next = draft.toMutableList()
            val moving = next.removeAt(current)
            next.add((target - 1).coerceIn(0, next.size), moving)
            draft = next
            val byKey = activeGroups.associateBy { it.key }
            vm.reorderOrders(next.mapNotNull(byKey::get).flatMap { it.orders.map(Order::code) })
        }
        editMarker = null
        editNumberText = ""
        editOnMap = false
    }
'''
if old in s and 'fun commitGroupedDirectStt()' not in s:
    s=s.replace(old,new,1)

old='''        onSaveRoute = { confirmSave = true },
        onEditStt = { marker ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
            }
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },'''
new='''        onSaveRoute = {
            editMarker = null
            editNumberText = ""
            editOnMap = false
            editing = false
        },
        onEditStt = { marker, fromMap ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
                editOnMap = fromMap
            }
        },
        editingCode = editMarker?.order?.code,
        editingNumberText = editNumberText,
        editingOnMap = editOnMap,
        onEditingNumberChange = { editNumberText = it.filter(Char::isDigit).take(4) },
        onCommitEdit = { commitGroupedDirectStt() },
        onCancelEdit = {
            editMarker = null
            editNumberText = ""
            editOnMap = false
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },'''
if old in s:
    s=s.replace(old,new,1)

# Disable old modal STT editor. Inline editor now owns this interaction.
s=s.replace('''    editMarker?.let { marker ->
        val maxStt = draft.size.coerceAtLeast(1)''','''    if (false) editMarker?.let { marker ->
        val maxStt = draft.size.coerceAtLeast(1)''',1)

# 2) ActionButton accepts click handlers used by grouped action bar.
old='''fun RowScope.ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean = false
) {'''
new='''fun RowScope.ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean = false,
    onClick: () -> Unit = {}
) {'''
if old in s:
    s=s.replace(old,new,1)

old='''        Modifier
            .height(36.dp)
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))'''
new='''        Modifier
            .height(36.dp)
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }'''
if old in s:
    s=s.replace(old,new,1)

p.write_text(s)
print('grouped delivery compile/direct STT fixes applied')
