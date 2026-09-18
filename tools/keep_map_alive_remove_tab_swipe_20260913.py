from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# Xóa hoàn toàn code/import phục vụ vuốt ngang đổi tab.
for line in [
    'import androidx.compose.foundation.gestures.draggable\n',
    'import androidx.compose.foundation.gestures.rememberDraggableState\n',
    'import androidx.compose.foundation.gestures.Orientation\n',
]:
    s = s.replace(line, '')

start_marker = '            var swipeDx by remember { mutableFloatStateOf(0f) }\n'
end_marker = '\n        }\n        AppScreen.CUSTOMER_DETAIL -> {'

if start_marker not in s:
    # Đã áp dụng rồi thì không làm gì để workflow chạy lặp an toàn.
    if 'MapScreen luôn được giữ trong composition để MapView không bị tạo lại khi đổi tab.' in s:
        print('persistent map/no-swipe patch already applied')
        raise SystemExit(0)
    raise SystemExit('main tab swipe block not found')

start = s.index(start_marker)
end = s.index(end_marker, start)

replacement = r'''            // MapScreen luôn được giữ trong composition để MapView không bị tạo lại khi đổi tab.
            // Các tab khác chỉ phủ một lớp giao diện lên trên; vì vậy đổi tab không còn reload bản đồ.
            Box(Modifier.fillMaxSize().padding(padding).background(Background)) {
                MapScreen(
                    vm = vm,
                    focusOrderCode = mapFocusOrderCode,
                    onFocusConsumed = { mapFocusOrderCode = null },
                    onOpenOrder = { code -> returnOrderCode = code; tab = Tab.ORDERS }
                )

                when (tab) {
                    Tab.MAP -> Unit
                    Tab.ORDERS -> Box(Modifier.fillMaxSize().background(Background)) {
                        OrderListScreen(
                            vm = vm,
                            focusOrderCode = returnOrderCode,
                            onFocusConsumed = { returnOrderCode = null },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onCustomerClick = { order ->
                                returnOrderCode = order.code
                                fun normalizedPhone(raw: String): String {
                                    val digits = raw.filter(Char::isDigit)
                                    return when {
                                        digits.startsWith("0084") -> "0" + digits.drop(4)
                                        digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
                                        else -> digits
                                    }
                                }
                                val wanted = normalizedPhone(order.phone)
                                val customer = vm.customers.firstOrNull { c ->
                                    normalizedPhone(c.phone) == wanted ||
                                        c.extraPhones.any { normalizedPhone(it.number) == wanted }
                                }
                                if (customer != null) {
                                    selectedCustomerId = customer.id
                                    screen = AppScreen.CUSTOMER_DETAIL
                                } else {
                                    Toast.makeText(context, "Không tìm thấy khách theo SĐT ${order.phone}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    Tab.CUSTOMERS -> Box(Modifier.fillMaxSize().background(Background)) {
                        CustomerListScreen(
                            customers = vm.customers,
                            onCustomerClick = {
                                returnOrderCode = null
                                selectedCustomerId = it.id
                                screen = AppScreen.CUSTOMER_DETAIL
                            },
                            onAddCustomer = {
                                selectedCustomerId = null
                                formIsNew = true
                                screen = AppScreen.CUSTOMER_FORM
                            }
                        )
                    }
                }
            }'''

s = s[:start] + replacement + s[end:]
p.write_text(s)
print('removed horizontal tab swipe and kept MapScreen alive across tabs')
