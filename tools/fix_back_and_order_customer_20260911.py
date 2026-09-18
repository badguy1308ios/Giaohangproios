from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Add hardware Back handling inside GiaoHangApp after selectedCustomer.
needle='''    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)\n\n    when (screen) {'''
replacement='''    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)\n    val context = LocalContext.current\n    var lastBackPressAt by remember { mutableLongStateOf(0L) }\n\n    androidx.activity.compose.BackHandler(enabled = true) {\n        when (screen) {\n            AppScreen.CUSTOMER_DETAIL -> screen = AppScreen.MAIN\n            AppScreen.CUSTOMER_FORM -> screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL\n            AppScreen.SETTINGS -> screen = AppScreen.MAIN\n            AppScreen.MONEY_LEDGER -> screen = AppScreen.SETTINGS\n            AppScreen.MAIN -> {\n                val now = android.os.SystemClock.elapsedRealtime()\n                if (now - lastBackPressAt <= 2000L) {\n                    (context as? android.app.Activity)?.finish()\n                } else {\n                    lastBackPressAt = now\n                    Toast.makeText(context, "Bấm Back lần nữa để thoát", Toast.LENGTH_SHORT).show()\n                }\n            }\n        }\n    }\n\n    when (screen) {'''
if needle in s:
    s=s.replace(needle,replacement,1)

# Wire Orders tab to customer lookup by normalized phone (name is intentionally ignored).
old='''                        Tab.ORDERS -> OrderListScreen(vm.orders)'''
new='''                        Tab.ORDERS -> OrderListScreen(\n                            orders = vm.orders,\n                            onCustomerClick = { order ->\n                                fun normalizedPhone(raw: String): String {\n                                    val digits = raw.filter(Char::isDigit)\n                                    return when {\n                                        digits.startsWith("0084") -> "0" + digits.drop(4)\n                                        digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)\n                                        else -> digits\n                                    }\n                                }\n                                val wanted = normalizedPhone(order.phone)\n                                val customer = vm.customers.firstOrNull { c ->\n                                    normalizedPhone(c.phone) == wanted ||\n                                        c.extraPhones.any { normalizedPhone(it.number) == wanted }\n                                }\n                                if (customer != null) {\n                                    selectedCustomerId = customer.id\n                                    screen = AppScreen.CUSTOMER_DETAIL\n                                } else {\n                                    Toast.makeText(context, "Không tìm thấy khách theo SĐT ${order.phone}", Toast.LENGTH_SHORT).show()\n                                }\n                            }\n                        )'''
if old in s:
    s=s.replace(old,new,1)

# Extend OrderListScreen and OrderCard callbacks.
s=s.replace('fun OrderListScreen(orders: List<Order>) {','fun OrderListScreen(orders: List<Order>, onCustomerClick: (Order) -> Unit) {',1)
s=s.replace('itemsIndexed(filteredOrders) { index, order -> OrderCard(index+1, order) }','itemsIndexed(filteredOrders) { index, order -> OrderCard(index + 1, order, onCustomerClick) }',1)
s=s.replace('fun OrderCard(index: Int, order: Order) {','fun OrderCard(index: Int, order: Order, onCustomerClick: (Order) -> Unit) {',1)
s=s.replace('OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}")','OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = { onCustomerClick(order) })',1)

# Make order info row optionally clickable; only the customer row uses it.
s=s.replace('''fun OrderInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {\n    Row(\n        Modifier\n            .fillMaxWidth()\n            .padding(vertical = 1.dp),''','''fun OrderInfoRow(\n    icon: androidx.compose.ui.graphics.vector.ImageVector,\n    text: String,\n    onClick: (() -> Unit)? = null\n) {\n    val rowModifier = Modifier\n        .fillMaxWidth()\n        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)\n        .padding(vertical = 1.dp)\n    Row(\n        rowModifier,''',1)

p.write_text(s)
