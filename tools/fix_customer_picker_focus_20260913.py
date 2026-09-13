from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Customer coordinate picker: saved coordinate wins; GPS only focuses once for a new customer.
old='''    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }
    val start = if (focusUserLocation) (driverLocation ?: initialPoint ?: DEFAULT_MAP_POINT) else (initialPoint ?: DEFAULT_MAP_POINT)

    // GPS thường trả về sau khi dialog đã mở. Khi có vị trí thật, chọn và focus ngay vào người dùng.
    LaunchedEffect(driverLocation, focusUserLocation) {
        if (focusUserLocation) driverLocation?.let { selected = it }
    }'''
new='''    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }
    val start = initialPoint ?: (if (focusUserLocation) driverLocation else null) ?: DEFAULT_MAP_POINT
    var didInitialGpsFocus by remember { mutableStateOf(initialPoint != null || !focusUserLocation) }

    // Chỉ focus GPS đúng một lần khi khách chưa có tọa độ.
    LaunchedEffect(driverLocation, focusUserLocation, initialPoint) {
        if (!didInitialGpsFocus && initialPoint == null && focusUserLocation) {
            driverLocation?.let { gps ->
                selected = gps
                didInitialGpsFocus = true
            }
        }
    }'''
if old in s:
    s=s.replace(old,new,1)
else:
    s=s.replace('''        if (!didInitialGpsFocus && initialPoint == null && focusUserLocation && driverLocation != null) {
            selected = driverLocation
            didInitialGpsFocus = true
        }''','''        if (!didInitialGpsFocus && initialPoint == null && focusUserLocation) {
            driverLocation?.let { gps ->
                selected = gps
                didInitialGpsFocus = true
            }
        }''',1)

# Cross-tab order -> map navigation: keep callback in OrderListScreen even after older scripts rewrite the screen.
order_sig='''fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {'''
order_sig_new='''fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {'''
if order_sig in s:
    s=s.replace(order_sig,order_sig_new,1)

old_card='''itemsIndexed(filteredOrders) { index, order -> OrderCard(index+1,order,onCustomerClick) }'''
new_card='''itemsIndexed(filteredOrders) { index, order -> OrderCard(index+1,order,onCustomerClick,onNumberClick={ onNumberClick(order) }) }'''
if old_card in s:
    s=s.replace(old_card,new_card,1)

# Route ordering methods used by the map tools.
if 'fun reorderOrders(codes: List<String>)' not in s and 'fun reorderOrders(codes:List<String>)' not in s:
    anchor='''    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }
'''
    addition='''    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }

    fun reorderOrders(codes: List<String>) {
        val rank = codes.withIndex().associate { it.value to it.index }
        val old = orderState.toList()
        orderState.clear()
        orderState.addAll(old.sortedWith(compareBy<Order> { rank[it.code] ?: Int.MAX_VALUE }.thenBy { old.indexOf(it) }))
        savePersistentData()
    }
'''
    if anchor in s:
        s=s.replace(anchor,addition,1)

# OrderCard uses combinedClickable, which requires the experimental foundation opt-in.
card_decl='''@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit, onNumberClick: () -> Unit = {}) {'''
card_decl_new='''@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit, onNumberClick: () -> Unit = {}) {'''
if card_decl in s and '@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n@Composable\nfun OrderCard' not in s:
    s=s.replace(card_decl,card_decl_new,1)

p.write_text(s)
print('customer picker focus and final Kotlin compile fixes applied')
