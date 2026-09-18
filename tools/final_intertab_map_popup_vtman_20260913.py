from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()
# gesture imports
if 'import androidx.compose.foundation.gestures.detectHorizontalDragGestures' not in s:
    s=s.replace('import androidx.compose.foundation.combinedClickable\n','import androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\nimport androidx.compose.ui.input.pointer.pointerInput\n')
# Add focus bridge parameters to MapScreen and BaseMapScreen from current source (legacy or improved)
s=s.replace('fun MapScreen(vm: MainViewModel) {','fun MapScreen(vm: MainViewModel, focusOrderCode: String? = null, onFocusConsumed: () -> Unit = {}, onOpenOrder: (String) -> Unit = {}) {',1)
# legacy BaseMapScreen call
s=s.replace('''        onImportStt = { importLauncher.launch("text/*") }\n    )''','''        onImportStt = { importLauncher.launch("text/*") },\n        focusOrderCode = focusOrderCode,\n        onFocusConsumed = onFocusConsumed,\n        onOpenOrder = onOpenOrder\n    )''',1)
# improved BaseMapScreen call variant
s=s.replace('''        onImportStt = { importLauncher.launch("text/*") }\n    )\n\n    editMarker?.let''','''        onImportStt = { importLauncher.launch("text/*") },\n        focusOrderCode = focusOrderCode,\n        onFocusConsumed = onFocusConsumed,\n        onOpenOrder = onOpenOrder\n    )\n\n    editMarker?.let''',1)
# BaseMapScreen signature: insert bridge params before closing
needle='''    onExportStt: () -> Unit,\n    onImportStt: () -> Unit\n) {'''
repl='''    onExportStt: () -> Unit,\n    onImportStt: () -> Unit,\n    focusOrderCode: String? = null,\n    onFocusConsumed: () -> Unit = {},\n    onOpenOrder: (String) -> Unit = {}\n) {'''
# replace the BaseMapScreen occurrence only
base=s.find('private fun BaseMapScreen(')
pos=s.find(needle,base)
if pos>=0: s=s[:pos]+s[pos:].replace(needle,repl,1)
# selected state works for either selectedMarker or selectedOrderCode implementation
base=s.find('private fun BaseMapScreen(')
end=s.find('// Bottom sheet dạng danh sách',base)
chunk=s[base:end]
if 'LaunchedEffect(focusOrderCode, mappedOrders)' not in chunk:
    anchor='''    val mappedOrders = remember(orders, customers, driverLocation) {'''
    # insert effect after mappedOrders block by locating selectedMarker line if improved, otherwise before Column
    insert_at=chunk.find('    Column(Modifier.fillMaxSize()', chunk.find(anchor))
    effect='''    LaunchedEffect(focusOrderCode, mappedOrders) {\n        val code = focusOrderCode ?: return@LaunchedEffect\n        if (mappedOrders.any { it.order.code == code }) {\n            selectedOrderCode = code\n        }\n        onFocusConsumed()\n    }\n\n'''
    if 'var selectedOrderCode by remember' not in chunk:
        chunk=chunk.replace('var selectedMarker by remember { mutableStateOf<MapOrderMarker?>(null) }','var selectedOrderCode by remember { mutableStateOf<String?>(null) }')
        # normalize references in legacy
        chunk=chunk.replace('selectedOrderNumber = selectedMarker?.number','selectedOrderNumber = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }?.number')
        chunk=chunk.replace('onOrderSelected = { selectedMarker = it }','onOrderSelected = { selectedOrderCode = it.order.code }')
        chunk=chunk.replace('selectedNumber = selectedMarker?.number','selectedNumber = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }?.number')
    insert_at=chunk.find('    Column(Modifier.fillMaxSize()')
    chunk=chunk[:insert_at]+effect+chunk[insert_at:]
# Map list row body click opens order, number click remains focus. improved patch has onNumberClick.
chunk=chunk.replace('onOrderClick = { selectedOrderCode = it.order.code },','onOrderClick = { onOpenOrder(it.order.code) },')
# if legacy bottom sheet lacks number callback, final improve script normally supplies it; leave fallback.
s=s[:base]+chunk+s[end:]
# OrderListScreen bridge and NumberCircle click
s=s.replace('''    onFocusConsumed: () -> Unit = {},\n    onCustomerClick: (Order) -> Unit\n) {''','''    onFocusConsumed: () -> Unit = {},\n    onCustomerClick: (Order) -> Unit,\n    onNumberClick: (Order) -> Unit = {}\n) {''',1)
s=s.replace('Box(Modifier.weight(1f)){OrderCard(index+1,o,onCustomerClick)}','Box(Modifier.weight(1f)){OrderCard(index+1,o,onCustomerClick,onNumberClick)}',1)
s=s.replace('fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {','fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit, onNumberClick: (Order) -> Unit = {}) {',1)
s=s.replace('''                NumberCircle(index, selected = true)\n\n                Spacer''','''                Box(Modifier.clip(CircleShape).clickable { onNumberClick(order) }) { NumberCircle(index, selected = true) }\n\n                Spacer''',1)
# Popup for long/truncated order info: clicking shop/address/item shows full value. customer keeps its existing action.
s=s.replace('OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })','OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer }, showFullPopup = true)',1)
s=s.replace('OrderInfoRow(Icons.Default.LocationOn, order.address)','OrderInfoRow(Icons.Default.LocationOn, order.address, showFullPopup = true)',1)
s=s.replace('OrderInfoRow(Icons.Default.Inventory2, order.item)','OrderInfoRow(Icons.Default.Inventory2, order.item, showFullPopup = true)',1)
s=s.replace('''    text: String,\n    onClick: (() -> Unit)? = null\n) {\n    val rowModifier = Modifier\n        .fillMaxWidth()\n        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)''','''    text: String,\n    onClick: (() -> Unit)? = null,\n    showFullPopup: Boolean = false\n) {\n    var showPopup by remember { mutableStateOf(false) }\n    val rowModifier = Modifier\n        .fillMaxWidth()\n        .then(if (onClick != null) Modifier.clickable { onClick() } else if (showFullPopup) Modifier.clickable { showPopup = true } else Modifier)''',1)
# add dialog at end of OrderInfoRow before Tag
marker='''    }\n}\n\n@Composable\nfun Tag(text: String)'''
dialog='''    }\n    if (showPopup) {\n        Dialog(onDismissRequest = { showPopup = false }) {\n            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {\n                Text(text, Modifier.padding(16.dp), color = Navy, fontSize = 15.sp)\n            }\n        }\n    }\n}\n\n@Composable\nfun Tag(text: String)'''
s=s.replace(marker,dialog,1)
# Main app: focus bridges and horizontal swipe on main content
s=s.replace('var returnOrderCode by remember { mutableStateOf<String?>(null) }','''var returnOrderCode by remember { mutableStateOf<String?>(null) }\n    var mapFocusOrderCode by remember { mutableStateOf<String?>(null) }''',1)
s=s.replace('''            Box(Modifier.fillMaxSize().padding(padding).background(Background)) {''','''            var swipeDx by remember { mutableFloatStateOf(0f) }\n            Box(Modifier.fillMaxSize().padding(padding).background(Background).pointerInput(tab) {\n                detectHorizontalDragGestures(\n                    onDragStart = { swipeDx = 0f },\n                    onHorizontalDrag = { _, amount -> swipeDx += amount },\n                    onDragEnd = {\n                        if (swipeDx < -90f) tab = when(tab){ Tab.MAP -> Tab.ORDERS; Tab.ORDERS -> Tab.CUSTOMERS; Tab.CUSTOMERS -> Tab.CUSTOMERS }\n                        else if (swipeDx > 90f) tab = when(tab){ Tab.MAP -> Tab.MAP; Tab.ORDERS -> Tab.MAP; Tab.CUSTOMERS -> Tab.ORDERS }\n                        swipeDx = 0f\n                    }\n                )\n            }) {''',1)
s=s.replace('Tab.MAP -> MapScreen(vm)','''Tab.MAP -> MapScreen(vm, focusOrderCode = mapFocusOrderCode, onFocusConsumed = { mapFocusOrderCode = null }, onOpenOrder = { code -> returnOrderCode = code; tab = Tab.ORDERS })''',1)
# add number callback after OrderList onCustomer block closing by targeted end before CUSTOMERS
needle='''                            }\n                        )\n                        Tab.CUSTOMERS ->'''
repl='''                            },\n                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP }\n                        )\n                        Tab.CUSTOMERS ->'''
s=s.replace(needle,repl,1)
p.write_text(s)
print('final inter-tab/map/popup patch applied')