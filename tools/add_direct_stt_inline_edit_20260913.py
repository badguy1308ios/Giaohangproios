from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

MARKER = '// DIRECT_STT_INLINE_EDIT_V1'
if MARKER in s:
    print('direct STT inline edit already integrated; no changes required')
    raise SystemExit(0)

# Needed for autofocus when tapping an STT bubble / list number.
if 'import androidx.compose.ui.focus.focusRequester\n' not in s:
    anchor = 'import androidx.compose.ui.draw.clip\n'
    if anchor not in s:
        raise SystemExit('focus import anchor missing')
    s = s.replace(anchor, anchor + 'import androidx.compose.ui.focus.focusRequester\n', 1)

old = '''    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(vm.orders.map { it.code }) }
    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }
'''
new = '''    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(vm.orders.map { it.code }) }
    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }
    var editOnMap by remember { mutableStateOf(false) }
    ''' + MARKER + '''

    fun commitDirectStt() {
        val marker = editMarker ?: return
        val maxStt = draft.size.coerceAtLeast(1)
        val target = editNumberText.toIntOrNull()?.coerceIn(1, maxStt) ?: return
        val current = draft.indexOf(marker.order.code)
        if (current >= 0) {
            val next = draft.toMutableList()
            next.removeAt(current)
            next.add((target - 1).coerceIn(0, next.size), marker.order.code)
            draft = next
            // Lưu ngay để bong bóng, list bản đồ và tab Chi tiết đơn luôn cùng một STT.
            vm.reorderOrders(next)
        }
        editMarker = null
        editNumberText = ""
        editOnMap = false
    }
'''
if old not in s:
    raise SystemExit('MapScreen edit state anchor missing')
s = s.replace(old, new, 1)

old = '''        onSaveRoute = { confirmSave = true },
        onEditStt = { marker ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
            }
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },'''
new = '''        onSaveRoute = {
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
        onCommitEdit = { commitDirectStt() },
        onCancelEdit = {
            editMarker = null
            editNumberText = ""
            editOnMap = false
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },'''
if old not in s:
    raise SystemExit('MapScreen BaseMap callbacks anchor missing')
s = s.replace(old, new, 1)

# Remove the old modal STT editor + save confirmation. Edits are now inline and persisted immediately.
start = s.find('\n    editMarker?.let { marker ->', s.find('fun MapScreen('))
end = s.find('\n}\n\n@Composable\nprivate fun BaseMapScreen(', start)
if start < 0 or end < 0:
    raise SystemExit('old STT dialog block anchor missing')
s = s[:start] + s[end:]

old = '''    onSaveRoute: () -> Unit,
    onEditStt: (MapOrderMarker) -> Unit,
    onExportStt: () -> Unit,'''
new = '''    onSaveRoute: () -> Unit,
    onEditStt: (MapOrderMarker, Boolean) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    editingOnMap: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onExportStt: () -> Unit,'''
if old not in s:
    raise SystemExit('BaseMapScreen signature anchor missing')
s = s.replace(old, new, 1)

old = '''                onOrderSelected = { selectedOrderCode = it.order.code },
                onEditStt = onEditStt
            )'''
new = '''                onOrderSelected = { selectedOrderCode = it.order.code },
                onEditStt = { marker -> onEditStt(marker, true) },
                editingCode = editingCode,
                editingNumberText = editingNumberText,
                showInlineEditor = editingStt && editingOnMap,
                onEditingNumberChange = onEditingNumberChange,
                onCommitEdit = onCommitEdit,
                onCancelEdit = onCancelEdit
            )'''
if old not in s:
    raise SystemExit('GoongOrderMap call anchor missing')
s = s.replace(old, new, 1)

old = '''                    onNumberClick = { marker ->
                        selectedOrderCode = marker.order.code
                        if (editingStt) onEditStt(marker)
                    },
                    onNavigate = { marker ->'''
new = '''                    onNumberClick = { marker ->
                        selectedOrderCode = marker.order.code
                        if (editingStt) onEditStt(marker, false)
                    },
                    editingCode = editingCode,
                    editingNumberText = editingNumberText,
                    editingOnMap = editingOnMap,
                    onEditingNumberChange = onEditingNumberChange,
                    onCommitEdit = onCommitEdit,
                    onCancelEdit = onCancelEdit,
                    onNavigate = { marker ->'''
if old not in s:
    raise SystemExit('MapOrderBottomSheet call anchor missing')
s = s.replace(old, new, 1)

old = '''    onOrderClick: (MapOrderMarker) -> Unit,
    onNumberClick: (MapOrderMarker) -> Unit,
    onNavigate: (MapOrderMarker) -> Unit,'''
new = '''    onOrderClick: (MapOrderMarker) -> Unit,
    onNumberClick: (MapOrderMarker) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    editingOnMap: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onNavigate: (MapOrderMarker) -> Unit,'''
if old not in s:
    raise SystemExit('MapOrderBottomSheet signature anchor missing')
s = s.replace(old, new, 1)

s = s.replace(
    'Text("Chạm vào vòng tròn STT hoặc bong bóng trên bản đồ để đổi vị trí.", color = TextGray, fontSize = 10.sp)',
    'Text("Chạm STT trong list hoặc bong bóng trên bản đồ rồi gõ số mới trực tiếp.", color = TextGray, fontSize = 10.sp)',
    1
)

old = '''                        editingStt = editingStt,
                        onClick = { onOrderClick(marker) },
                        onNumberClick = { onNumberClick(marker) },
                        onNavigate = { onNavigate(marker) }'''
new = '''                        editingStt = editingStt,
                        editingCode = editingCode,
                        editingNumberText = editingNumberText,
                        inlineEditingHere = !editingOnMap,
                        onEditingNumberChange = onEditingNumberChange,
                        onCommitEdit = onCommitEdit,
                        onCancelEdit = onCancelEdit,
                        onClick = { onOrderClick(marker) },
                        onNumberClick = { onNumberClick(marker) },
                        onNavigate = { onNavigate(marker) }'''
if old not in s:
    raise SystemExit('MapOrderListRow call anchor missing')
s = s.replace(old, new, 1)

old = '''    marker: MapOrderMarker,
    selected: Boolean,
    editingStt: Boolean,
    onClick: () -> Unit,
    onNumberClick: () -> Unit,
    onNavigate: () -> Unit
) {'''
new = '''    marker: MapOrderMarker,
    selected: Boolean,
    editingStt: Boolean,
    editingCode: String?,
    editingNumberText: String,
    inlineEditingHere: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onClick: () -> Unit,
    onNumberClick: () -> Unit,
    onNavigate: () -> Unit
) {'''
if old not in s:
    raise SystemExit('MapOrderListRow signature anchor missing')
s = s.replace(old, new, 1)

old = '''            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onNumberClick() },
                contentAlignment = Alignment.Center
            ) {
                NumberCircle(marker.number, selected = selected || editingStt)
            }
            Spacer(Modifier.width(4.dp))'''
new = '''            if (editingStt && inlineEditingHere && editingCode == order.code) {
                val focusRequester = remember(order.code) { androidx.compose.ui.focus.FocusRequester() }
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                LaunchedEffect(editingCode) {
                    kotlinx.coroutines.delay(80)
                    focusRequester.requestFocus()
                }
                OutlinedTextField(
                    value = editingNumberText,
                    onValueChange = onEditingNumberChange,
                    modifier = Modifier.width(58.dp).height(52.dp).focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        onCommitEdit()
                        focusManager.clearFocus()
                    }),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
            } else {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onNumberClick() },
                    contentAlignment = Alignment.Center
                ) {
                    NumberCircle(marker.number, selected = selected || editingStt)
                }
            }
            Spacer(Modifier.width(4.dp))'''
if old not in s:
    raise SystemExit('MapOrderListRow number anchor missing')
s = s.replace(old, new, 1)

old = '''    editingStt: Boolean,
    onToggleExpand: () -> Unit,
    onOrderSelected: (MapOrderMarker) -> Unit,
    onEditStt: (MapOrderMarker) -> Unit
) {'''
new = '''    editingStt: Boolean,
    onToggleExpand: () -> Unit,
    onOrderSelected: (MapOrderMarker) -> Unit,
    onEditStt: (MapOrderMarker) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    showInlineEditor: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit
) {'''
if old not in s:
    raise SystemExit('GoongOrderMap signature anchor missing')
s = s.replace(old, new, 1)

# Insert a compact editable STT field directly over the selected map bubble.
anchor = '''    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialDriverFocus by remember { mutableStateOf(false) }
'''
insert = '''    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialDriverFocus by remember { mutableStateOf(false) }
    var inlineEditorOffset by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
    val inlineEditorMarker = orders.firstOrNull { it.order.code == editingCode }

    LaunchedEffect(map, inlineEditorMarker, showInlineEditor) {
        if (!showInlineEditor) return@LaunchedEffect
        val readyMap = map ?: return@LaunchedEffect
        val marker = inlineEditorMarker ?: return@LaunchedEffect
        // Chờ camera focus marker xong rồi đặt textbox ngay trên bong bóng.
        kotlinx.coroutines.delay(260)
        val screenPoint = readyMap.projection.toScreenLocation(LatLng(marker.point.latitude, marker.point.longitude))
        val density = context.resources.displayMetrics.density
        val editorWidth = (64f * density).toInt()
        val x = (screenPoint.x - editorWidth / 2f).toInt().coerceIn(0, (mapView.width - editorWidth).coerceAtLeast(0))
        val y = (screenPoint.y - 64f * density).toInt().coerceAtLeast(0)
        inlineEditorOffset = androidx.compose.ui.unit.IntOffset(x, y)
    }
'''
if anchor not in s:
    raise SystemExit('GoongOrderMap state anchor missing')
s = s.replace(anchor, insert, 1)

anchor = '''        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {'''
inline_editor = '''        if (editingStt && showInlineEditor && inlineEditorMarker != null) {
            val focusRequester = remember(inlineEditorMarker.order.code) { androidx.compose.ui.focus.FocusRequester() }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            LaunchedEffect(editingCode) {
                kotlinx.coroutines.delay(120)
                focusRequester.requestFocus()
            }
            Surface(
                modifier = Modifier.offset { inlineEditorOffset }.width(64.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, Orange)
            ) {
                OutlinedTextField(
                    value = editingNumberText,
                    onValueChange = onEditingNumberChange,
                    modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        onCommitEdit()
                        focusManager.clearFocus()
                    }),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {'''
if anchor not in s:
    raise SystemExit('GoongOrderMap controls anchor missing')
s = s.replace(anchor, inline_editor, 1)

p.write_text(s)
print('direct inline STT editing integrated')
