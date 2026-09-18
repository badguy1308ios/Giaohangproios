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

# 3) Ensure the grouped "Đã giao" action exists in MainViewModel.
# It marks every MVĐ linked to the same Customer ID as delivered; map/list route filtering
# then removes that whole stop while the order tab keeps the delivered block at the bottom.
if 'fun markDeliveredGroup(code: String)' not in s:
    anchor='''    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }
'''
    addition='''    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }

    fun markDeliveredGroup(code: String) {
        val base = orderState.firstOrNull { it.code == code } ?: return
        val customer = findCustomerByPhone(base.phone)
        if (customer == null) {
            val i = orderState.indexOfFirst { it.code == code }
            if (i >= 0) orderState[i] = orderState[i].copy(status = "Đã giao")
        } else {
            val customerPhones = (listOf(customer.phone) + customer.extraPhones.map { it.number })
                .map(::normalizeCustomerPhone)
                .filter(String::isNotBlank)
                .toSet()
            orderState.indices.forEach { i ->
                if (normalizeCustomerPhone(orderState[i].phone) in customerPhones) {
                    orderState[i] = orderState[i].copy(status = "Đã giao")
                }
            }
        }
        savePersistentData()
    }
'''
    if anchor not in s:
        raise SystemExit('deleteOrder anchor missing while adding markDeliveredGroup')
    s=s.replace(anchor,addition,1)

p.write_text(s)
print('grouped delivery compile/direct STT fixes applied')
