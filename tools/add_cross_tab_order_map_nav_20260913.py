from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Main app state for focusing an order on the map.
needle='    var returnOrderCode by remember { mutableStateOf<String?>(null) }\n'
if 'var mapFocusOrderCode' not in s:
    s=s.replace(needle,needle+'    var mapFocusOrderCode by remember { mutableStateOf<String?>(null) }\n',1)

# Map tab receives focus requests and row taps open the order tab.
s=s.replace(
'''                        Tab.MAP -> MapScreen(vm)''',
'''                        Tab.MAP -> MapScreen(
                            vm = vm,
                            focusOrderCode = mapFocusOrderCode,
                            onFocusConsumed = { mapFocusOrderCode = null },
                            onOpenOrder = { code -> returnOrderCode = code; tab = Tab.ORDERS }
                        )''',1)

# Order tab STT circle sends the same order to the map.
s=s.replace(
'''                            onFocusConsumed = { returnOrderCode = null },
                            onCustomerClick = { order ->''',
'''                            onFocusConsumed = { returnOrderCode = null },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onCustomerClick = { order ->''',1)

# OrderListScreen callback and OrderCard wiring.
s=s.replace(
'''    onFocusConsumed: () -> Unit = {},
    onCustomerClick: (Order) -> Unit''',
'''    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit''',1)
s=s.replace(
'''Box(Modifier.weight(1f)){OrderCard(index+1,o,onCustomerClick)}''',
'''Box(Modifier.weight(1f)){OrderCard(index+1,o,onCustomerClick,onNumberClick={onNumberClick(o)})}''',1)

# MapScreen API.
s=s.replace(
'''fun MapScreen(vm: MainViewModel) {''',
'''fun MapScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenOrder: (String) -> Unit = {}
) {''',1)

# BaseMapScreen call from MapScreen.
needle='''    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        editingStt = editing,'''
repl='''    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        editingStt = editing,
        focusOrderCode = focusOrderCode,
        onFocusConsumed = onFocusConsumed,
        onOpenOrder = onOpenOrder,'''
if needle not in s: raise SystemExit('BaseMapScreen call anchor missing')
s=s.replace(needle,repl,1)

# BaseMapScreen API.
needle='''private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    editingStt: Boolean,'''
repl='''private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    editingStt: Boolean,
    focusOrderCode: String?,
    onFocusConsumed: () -> Unit,
    onOpenOrder: (String) -> Unit,'''
if needle not in s: raise SystemExit('BaseMapScreen signature anchor missing')
s=s.replace(needle,repl,1)

# External focus selects matching marker/list row once.
needle='''    val selectedMarker = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }

    Column('''
repl='''    val selectedMarker = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }

    LaunchedEffect(focusOrderCode, mappedOrders) {
        val code = focusOrderCode ?: return@LaunchedEffect
        if (mappedOrders.any { it.order.code == code }) selectedOrderCode = code
        onFocusConsumed()
    }

    Column('''
if needle not in s: raise SystemExit('selected marker anchor missing')
s=s.replace(needle,repl,1)

# Whole row opens order details; only STT circle performs map focus/edit.
s=s.replace(
'''                    onOrderClick = { selectedOrderCode = it.order.code },''',
'''                    onOrderClick = { marker -> if (!editingStt) onOpenOrder(marker.order.code) },''',1)

# Keep focused order visible in map list.
needle='''    var routeMenuExpanded by remember { mutableStateOf(false) }
    Surface('''
repl='''    var routeMenuExpanded by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedNumber, orders) {
        val n = selectedNumber ?: return@LaunchedEffect
        val i = orders.indexOfFirst { it.number == n }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    Surface('''
if needle not in s: raise SystemExit('bottom sheet state anchor missing')
s=s.replace(needle,repl,1)
s=s.replace(
'''            LazyColumn(
                modifier = Modifier.fillMaxWidth(),''',
'''            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),''',1)

p.write_text(s)
print('cross-tab order/map navigation applied')
