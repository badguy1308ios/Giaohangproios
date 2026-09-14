from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

# Customer cards should show every stored gate photo (primary + address photos), without cropping.
old_head = '''@Composable
fun CustomerCard( // Một thẻ khách hàng gồm thông tin và hàng nút thao tác.
    customer: Customer, // Dữ liệu khách cần hiển thị.
    selected: Boolean, // Cho biết thẻ có đang được làm nổi bật hay không.
    onClick: () -> Unit // Callback mở chi tiết khách hàng.
) {
    val context = LocalContext.current // Lấy Context để hiển thị phản hồi ngắn khi bấm các nút demo.
'''
new_head = '''@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CustomerCard( // Một thẻ khách hàng gồm thông tin, toàn bộ ảnh cổng và hàng nút thao tác.
    customer: Customer, // Dữ liệu khách cần hiển thị.
    selected: Boolean, // Cho biết thẻ có đang được làm nổi bật hay không.
    onClick: () -> Unit // Callback mở chi tiết khách hàng.
) {
    val context = LocalContext.current // Lấy Context để hiển thị phản hồi ngắn khi bấm các nút demo.
    val gatePhotos = remember(customer.photoUri, customer.extraAddresses) {
        buildList {
            customer.photoUri.trim().takeIf { it.isNotBlank() }?.let(::add)
            customer.extraAddresses.mapNotNull { it.photoUri.trim().takeIf(String::isNotBlank) }.forEach(::add)
        }.distinct()
    }
'''
if old_head in s:
    s = s.replace(old_head, new_head, 1)
elif 'val gatePhotos = remember(customer.photoUri, customer.extraAddresses)' not in s:
    raise SystemExit('CustomerCard header anchor not found')

s = s.replace('''.fillMaxWidth() // Thẻ rộng hết vùng danh sách.
            .height(118.dp) // Chiều cao đủ cho phần thông tin và 4 nút thao tác.
            .clickable { onClick() },''', '''.fillMaxWidth() // Thẻ rộng hết vùng danh sách.
            .clickable { onClick() },''', 1)

s = s.replace('''.fillMaxSize() // Cột chiếm toàn bộ không gian của Card.
                .padding(horizontal = 7.dp, vertical = 5.dp) // Tạo khoảng cách với viền thẻ.
        ) {
            Row( // Hàng trên chứa avatar, tên, số điện thoại và mũi tên.''', '''.fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 6.dp) // Tạo khoảng cách với viền thẻ.
        ) {
            if (gatePhotos.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 2
                ) {
                    gatePhotos.forEachIndexed { index, uriText ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(112.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF2F2F2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    android.widget.ImageView(ctx).apply {
                                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                        adjustViewBounds = true
                                        setBackgroundColor(android.graphics.Color.rgb(242, 242, 242))
                                        contentDescription = "Ảnh cổng ${customer.name} ${index + 1}"
                                        runCatching { setImageURI(android.net.Uri.parse(uriText)) }
                                    }
                                },
                                update = { view ->
                                    view.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                    runCatching { view.setImageURI(android.net.Uri.parse(uriText)) }
                                }
                            )
                        }
                    }
                    if (gatePhotos.size % 2 == 1) Spacer(Modifier.weight(1f).height(1.dp))
                }
            }
            Row( // Hàng trên chứa avatar, tên, số điện thoại và mũi tên.''', 1)

# Row no longer needs weight because card height is content-driven.
s = s.replace('''.fillMaxWidth() // Hàng rộng toàn bộ thẻ.
                    .weight(1f), // Chiếm phần chiều cao còn lại sau hàng nút.''', '''.fillMaxWidth(), // Hàng rộng toàn bộ thẻ.''', 1)

p.write_text(s)
print('customer cards now show all stored gate photos at large fit-center size')
