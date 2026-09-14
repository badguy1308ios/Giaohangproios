from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# 1) Keep the clicked map order code plus the matched Customer ID while switching tabs.
old = '''    var formIsNew by remember { mutableStateOf(false) }
    var returnOrderCode by remember { mutableStateOf<String?>(null) }
    var mapFocusOrderCode by remember { mutableStateOf<String?>(null) }'''
new = '''    var formIsNew by remember { mutableStateOf(false) }
    var returnOrderCode by remember { mutableStateOf<String?>(null) }
    var returnOrderCustomerId by remember { mutableStateOf<Long?>(null) }
    var mapFocusOrderCode by remember { mutableStateOf<String?>(null) }'''
if old in s:
    s = s.replace(old, new, 1)
elif 'var returnOrderCustomerId by remember' not in s:
    raise SystemExit('returnOrderCustomerId anchor not found')

old = '''                    onFocusConsumed = { mapFocusOrderCode = null },
                    onOpenOrder = { code -> returnOrderCode = code; tab = Tab.ORDERS }
                )'''
new = '''                    onFocusConsumed = { mapFocusOrderCode = null },
                    onOpenOrder = { code ->
                        returnOrderCode = code
                        returnOrderCustomerId = buildDeliveryGroups(vm.orders, vm.customers)
                            .firstOrNull { group -> group.orders.any { it.code == code } }
                            ?.customer?.id
                        tab = Tab.ORDERS
                    }
                )'''
if old in s:
    s = s.replace(old, new, 1)
elif 'returnOrderCustomerId = buildDeliveryGroups(vm.orders, vm.customers)' not in s:
    raise SystemExit('MapScreen onOpenOrder wiring anchor not found')

old = '''                            vm = vm,
                            focusOrderCode = returnOrderCode,
                            onFocusConsumed = { returnOrderCode = null },'''
new = '''                            vm = vm,
                            focusOrderCode = returnOrderCode,
                            focusCustomerId = returnOrderCustomerId,
                            onFocusConsumed = {
                                returnOrderCode = null
                                returnOrderCustomerId = null
                            },'''
if old in s:
    s = s.replace(old, new, 1)
elif 'focusCustomerId = returnOrderCustomerId' not in s:
    raise SystemExit('OrderListScreen call anchor not found')

# 2) Order tab accepts an optional Customer ID filter associated with a map click.
old = '''fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},'''
new = '''fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    focusCustomerId: Long? = null,
    onFocusConsumed: () -> Unit = {},'''
if old in s:
    s = s.replace(old, new, 1)
elif 'focusCustomerId: Long? = null' not in s:
    raise SystemExit('OrderListScreen signature anchor not found')

old = '''    var keyword by remember { mutableStateOf("") }
    var showTools by remember { mutableStateOf(false) }'''
new = '''    var keyword by remember { mutableStateOf("") }
    var mapCustomerFilterId by remember { mutableStateOf<Long?>(null) }
    var showTools by remember { mutableStateOf(false) }'''
if old in s:
    s = s.replace(old, new, 1)
elif 'var mapCustomerFilterId by remember' not in s:
    raise SystemExit('mapCustomerFilterId state anchor not found')

# Fill the search textbox with the clicked MVĐ, but keep Customer ID as the actual grouped filter.
anchor = '''    val q = keyword.trim()

    fun matches(g: DeliveryGroup): Boolean = q.isBlank() ||'''
replacement = '''    val q = keyword.trim()

    LaunchedEffect(focusOrderCode, focusCustomerId) {
        val code = focusOrderCode ?: return@LaunchedEffect
        keyword = code
        mapCustomerFilterId = focusCustomerId
    }

    fun matches(g: DeliveryGroup): Boolean =
        mapCustomerFilterId?.let { wantedId -> g.customer?.id == wantedId } ?: (q.isBlank() ||'''
if anchor in s:
    s = s.replace(anchor, replacement, 1)
elif 'mapCustomerFilterId?.let { wantedId -> g.customer?.id == wantedId }' not in s:
    raise SystemExit('matches anchor not found')

# Close the parenthesis added around the normal text-search expression.
old = '''        } || (g.customer?.name?.contains(q, true) == true)

    // Tab Chi tiết đơn:'''
new = '''        } || (g.customer?.name?.contains(q, true) == true))

    // Tab Chi tiết đơn:'''
if old in s:
    s = s.replace(old, new, 1)
elif 'g.customer?.name?.contains(q, true) == true))' not in s:
    raise SystemExit('matches closing anchor not found')

# Manual search/clear must leave map-group mode and return to normal text search.
old = '''            SearchBox(keyword, { keyword = it }, { keyword = "" }) {'''
new = '''            SearchBox(
                keyword,
                { value ->
                    keyword = value
                    mapCustomerFilterId = null
                },
                {
                    keyword = ""
                    mapCustomerFilterId = null
                }
            ) {'''
if old in s:
    s = s.replace(old, new, 1)
elif 'mapCustomerFilterId = null' not in s:
    raise SystemExit('SearchBox anchor not found')

# Scanner search is also a fresh/manual search, not a continuation of a map group filter.
old = '''        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword = it }
    }'''
new = '''        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let {
            keyword = it
            mapCustomerFilterId = null
        }
    }'''
if old in s:
    s = s.replace(old, new, 1)

p.write_text(s)
print('map list click now opens order tab, fills MVĐ search, and shows all MVĐ for the same Customer ID')
