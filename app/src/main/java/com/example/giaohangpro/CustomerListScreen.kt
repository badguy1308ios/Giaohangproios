package com.example.giaohangpro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ================================================================
// 7. TAB KHÁCH HÀNG
// ================================================================
// Phần dưới đây được viết lại theo đúng bố cục ảnh mẫu:
// - Header màu cam ở trên cùng.
// - Ô tìm kiếm lớn có nút X để xóa nội dung.
// - Mỗi khách hàng là một thẻ lớn gồm avatar, tên, số điện thoại và 4 nút thao tác.
// - Khách đầu tiên được tô viền cam giống ảnh tham chiếu.
// - Nút dấu + nổi ở góc dưới bên trái.

@Composable
fun CustomerListScreen(
    customers: List<Customer>, // Nhận danh sách khách hàng từ ViewModel để hiển thị.
    onCustomerClick: (Customer) -> Unit, // Nhận callback để mở màn hình chi tiết khi bấm vào thẻ khách.
    onAddCustomer: () -> Unit // Callback mở form thêm khách hàng mới.
) {
    var keyword by remember { mutableStateOf("") } // Lưu nội dung người dùng đang nhập vào ô tìm kiếm.

    val nameQuery = normalizeCustomerSearch(keyword)
    val filteredCustomers = customers.filter { customer -> // Tạo danh sách mới chỉ gồm các khách phù hợp từ khóa.
        val query = keyword.trim() // Xóa khoảng trắng thừa ở đầu và cuối từ khóa.
        val matchesAnyPhone = sequenceOf(customer.phone)
            .plus(customer.extraPhones.asSequence().map(CustomerPhone::number))
            .any { phone -> matchesPhoneSearch(phone, query) }
        query.isBlank() || // Nếu chưa nhập gì thì giữ nguyên toàn bộ danh sách.
            normalizeCustomerSearch(customer.name).contains(nameQuery) || // Cho phép tìm theo tên, không phân biệt hoa/thường.
            normalizeCustomerSearch(customer.streetName).contains(nameQuery) || // Cho phép lọc theo Tên Đường đã lưu, không phân biệt hoa/thường/dấu.
            matchesAnyPhone || // Tìm cả SĐT chính/phụ với dạng 0, 84, +84 và ký tự phân cách.
            customer.address.contains(query, ignoreCase = true) // Cho phép tìm theo địa chỉ.
    }

    Box( // Dùng Box để nút + có thể nổi đè lên danh sách ở góc dưới.
        modifier = Modifier
            .fillMaxSize() // Chiếm toàn bộ vùng nội dung của tab.
            .background(Background) // Đặt màu nền xám xanh nhạt cho toàn màn hình.
    ) {
        Column(Modifier.fillMaxSize()) { // Xếp header, ô tìm kiếm và danh sách theo chiều dọc.

            CustomerSearchBox( // Hiển thị ô tìm kiếm riêng cho tab khách hàng.
                value = keyword, // Truyền nội dung tìm kiếm hiện tại vào ô nhập.
                onValueChange = { keyword = it }, // Cập nhật state mỗi khi người dùng gõ.
                onClear = { keyword = "" } // Xóa toàn bộ nội dung khi bấm biểu tượng X.
            )

            LazyColumn( // Danh sách cuộn dọc các khách hàng.
                modifier = Modifier
                    .fillMaxWidth() // Danh sách rộng bằng vùng màn hình.
                    .weight(1f), // Chiếm toàn bộ chiều cao còn lại dưới ô tìm kiếm.
                verticalArrangement = Arrangement.spacedBy(4.dp), // Tạo khoảng cách đều giữa các thẻ.
                contentPadding = PaddingValues( // Thêm khoảng trống quanh danh sách.
                    start = 6.dp, // Căn lề trái theo ảnh mẫu.
                    end = 6.dp, // Căn lề phải theo ảnh mẫu.
                    top = 6.dp, // Tạo khoảng cách từ ô tìm kiếm xuống thẻ đầu tiên.
                    bottom = 58.dp // Chừa chỗ để nút + không che thẻ cuối.
                )
            ) {
                itemsIndexed(filteredCustomers) { index, customer -> // Vẽ lần lượt từng khách sau khi đã lọc.
                    CustomerCard( // Gọi composable tạo giao diện một thẻ khách hàng.
                        customer = customer, // Truyền dữ liệu của khách hiện tại.
                        selected = index == 0 && keyword.isBlank(), // Chỉ tô nổi khách đầu tiên khi chưa tìm kiếm.
                        onClick = { onCustomerClick(customer) } // Bấm vùng thông tin sẽ mở chi tiết khách.
                    )
                }
            }
        }

        CustomerAddButton( // Nút + nổi phía dưới bên trái.
            modifier = Modifier
                .align(Alignment.BottomStart) // Ghim nút vào góc dưới bên trái của Box.
                .padding(start = 12.dp, bottom = 6.dp), // Đặt khoảng cách giống bố cục ảnh mẫu.
            onClick = onAddCustomer // Bấm dấu + sẽ mở màn hình thêm khách hàng mới.
        )
    }
}

@Composable
private fun CustomerSearchBox( // Ô tìm kiếm có giao diện giống ảnh tham chiếu.
    value: String, // Nội dung hiện tại trong ô nhập.
    onValueChange: (String) -> Unit, // Callback nhận nội dung mới khi người dùng nhập.
    onClear: () -> Unit // Callback xóa nội dung khi bấm nút X.
) {
    OutlinedTextField( // Dùng ô nhập có viền bo tròn.
        value = value, // Hiển thị state tìm kiếm hiện tại.
        onValueChange = onValueChange, // Gửi dữ liệu mới ngược về CustomerListScreen.
        modifier = Modifier
            .fillMaxWidth() // Ô tìm kiếm rộng hết chiều ngang.
            .padding(horizontal = 6.dp, vertical = 6.dp) // Tạo lề giống ảnh mẫu.
            .heightIn(min = 52.dp), // Đặt chiều cao tối thiểu để ô trông lớn, dễ chạm.
        placeholder = { // Nội dung gợi ý khi chưa nhập.
            Text(
                text = "Tìm tên, SĐT, địa chỉ, tên đường", // Đúng nội dung gợi ý theo yêu cầu UI.
                color = TextGray, // Dùng màu xám xanh cho chữ gợi ý.
                fontSize = 15.sp // Cỡ chữ gần với ảnh mẫu.
            )
        },
        leadingIcon = { // Biểu tượng bên trái ô tìm kiếm.
            Icon(
                imageVector = Icons.Default.Search, // Dùng icon kính lúp.
                contentDescription = "Tìm kiếm", // Mô tả hỗ trợ accessibility.
                tint = TextGray, // Đồng bộ màu với chữ gợi ý.
                modifier = Modifier.size(24.dp) // Tăng kích thước icon giống ảnh.
            )
        },
        trailingIcon = { // Biểu tượng X ở bên phải.
            IconButton(onClick = onClear) { // Bấm vào sẽ gọi callback xóa nội dung.
                Icon(
                    imageVector = Icons.Default.Close, // Dùng icon dấu X.
                    contentDescription = "Xóa nội dung tìm kiếm", // Mô tả hỗ trợ accessibility.
                    tint = TextGray, // Dùng màu xám xanh như ảnh mẫu.
                    modifier = Modifier.size(22.dp) // Giữ kích thước dễ thao tác.
                )
            }
        },
        singleLine = true, // Giữ ô tìm kiếm chỉ hiển thị một dòng.
        shape = RoundedCornerShape(15.dp), // Bo tròn bốn góc giống ảnh.
        colors = OutlinedTextFieldDefaults.colors( // Khai báo màu cho các trạng thái của ô nhập.
            focusedBorderColor = Border, // Không đổi sang màu quá nổi khi đang focus.
            unfocusedBorderColor = Border, // Viền trạng thái bình thường.
            focusedContainerColor = Color(0xFFF7F9FC), // Nền khi đang focus.
            unfocusedContainerColor = Color(0xFFF7F9FC), // Nền khi chưa focus.
            cursorColor = Orange // Dùng màu cam thương hiệu cho con trỏ.
        )
    )
}

@Composable
fun CustomerCard( // Một thẻ khách hàng gồm thông tin và hàng nút thao tác.
    customer: Customer, // Dữ liệu khách cần hiển thị.
    selected: Boolean, // Cho biết thẻ có đang được làm nổi bật hay không.
    onClick: () -> Unit // Callback mở chi tiết khách hàng.
) {
    val context = LocalContext.current // Lấy Context để hiển thị phản hồi ngắn khi bấm các nút demo.

    Card( // Dùng Material Card để tạo thẻ nền trắng bo góc.
        modifier = Modifier
            .fillMaxWidth() // Thẻ rộng hết vùng danh sách.
            .height(118.dp) // Chiều cao đủ cho phần thông tin và 4 nút thao tác.
            .clickable { onClick() }, // Bấm vào vùng trống/thông tin để mở chi tiết khách.
        shape = RoundedCornerShape(20.dp), // Bo góc lớn giống ảnh tham chiếu.
        colors = CardDefaults.cardColors( // Cấu hình màu nền thẻ.
            containerColor = if (selected) Color(0xFFFFF9F4) else CardWhite // Thẻ đầu có nền cam rất nhạt.
        ),
        border = androidx.compose.foundation.BorderStroke( // Tạo đường viền bao quanh thẻ.
            width = if (selected) 1.5.dp else 1.dp, // Thẻ được chọn có viền dày hơn nhẹ.
            color = if (selected) Orange else Border // Thẻ đầu viền cam, thẻ còn lại viền xám.
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Tạo bóng rất nhẹ để tách thẻ khỏi nền.
    ) {
        Column( // Xếp hàng thông tin ở trên và hàng nút thao tác ở dưới.
            modifier = Modifier
                .fillMaxSize() // Cột chiếm toàn bộ không gian của Card.
                .padding(horizontal = 7.dp, vertical = 5.dp) // Tạo khoảng cách với viền thẻ.
        ) {
            Row( // Hàng trên chứa avatar, tên, số điện thoại và mũi tên.
                modifier = Modifier
                    .fillMaxWidth() // Hàng rộng toàn bộ thẻ.
                    .weight(1f), // Chiếm phần chiều cao còn lại sau hàng nút.
                verticalAlignment = Alignment.CenterVertically // Căn giữa các phần tử theo chiều dọc.
            ) {
                CustomerAvatar( // Hiển thị avatar icon hoặc chữ viết tắt.
                    customer = customer, // Truyền dữ liệu khách để quyết định avatar.
                    selected = selected // Truyền trạng thái để đổi màu avatar.
                )

                Spacer(Modifier.width(8.dp)) // Tạo khoảng cách giữa avatar và phần chữ.

                Column(Modifier.weight(1f)) { // Cột tên + số điện thoại chiếm phần ngang còn lại.
                    Text(
                        text = customer.name, // Hiển thị tên khách.
                        color = Color(0xFF1E2C40), // Dùng màu chữ đậm gần ảnh mẫu.
                        fontSize = 13.sp, // Cỡ chữ lớn cho tên.
                        fontWeight = FontWeight.Bold, // Làm tên nổi bật.
                        maxLines = 1, // Không cho tên xuống dòng.
                        overflow = TextOverflow.Ellipsis // Tên dài sẽ hiện dấu ... giống ảnh.
                    )
                    Spacer(Modifier.height(3.dp)) // Tạo khoảng cách nhỏ giữa tên và số điện thoại.
                    Text(
                        text = customer.phone, // Hiển thị số điện thoại.
                        color = TextGray, // Dùng màu xám xanh nhạt hơn tên.
                        fontSize = 13.sp, // Cỡ chữ phù hợp phần phụ.
                        fontWeight = FontWeight.SemiBold // Tăng độ rõ của số điện thoại.
                    )
                }

                Icon( // Mũi tên sang phải ở mép phải thẻ.
                    imageVector = Icons.Default.ChevronRight, // Dùng icon chevron right.
                    contentDescription = "Xem chi tiết ${customer.name}", // Mô tả hỗ trợ accessibility.
                    tint = TextGray, // Màu xám xanh nhẹ.
                    modifier = Modifier.size(24.dp) // Kích thước tương tự ảnh mẫu.
                )
            }
            CustomerQuickActions(
                onZalo = {
                    val raw = if (customer.primaryCanZalo && customer.phone.isNotBlank()) customer.phone
                    else customer.extraPhones.firstOrNull { it.canZalo }?.number
                    raw?.let {
                        val number = it.filter(Char::isDigit)
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/$number")))
                        }
                    }
                },
                onSms = {
                    val raw = if (customer.primaryCanSms && customer.phone.isNotBlank()) customer.phone
                    else customer.extraPhones.firstOrNull { it.canSms }?.number
                    raw?.let {
                        val number = it.filter(Char::isDigit)
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("smsto:$number")))
                        }
                    }
                },
                onCall = {
                    val raw = if (customer.primaryCanCall && customer.phone.isNotBlank()) customer.phone
                    else customer.extraPhones.firstOrNull { it.canCall }?.number
                    raw?.let {
                        val number = it.filter(Char::isDigit)
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")))
                        }
                    }
                },
                onNavigate = {
                    pointFromStrings(customer.latitude, customer.longitude)?.let { openGoogleNavigation(context, it) }
                }
            )
        }
    }
}

@Composable
private fun CustomerAvatar( // Tạo avatar tròn ở bên trái thẻ khách.
    customer: Customer, // Dữ liệu khách dùng để đọc chữ viết tắt.
    selected: Boolean // Trạng thái thẻ để chọn màu avatar.
) {
    Box( // Khung hình tròn chứa icon hoặc chữ cái.
        modifier = Modifier
            .size(34.dp) // Kích thước avatar lớn giống ảnh mẫu.
            .clip(CircleShape) // Cắt khung thành hình tròn.
            .background(if (selected) Orange else Color(0xFF35475B)), // Khách đầu màu cam, các khách khác màu xanh xám.
        contentAlignment = Alignment.Center // Căn giữa nội dung avatar.
    ) {
        if (customer.initials.isBlank()) { // Nếu không có chữ viết tắt thì hiển thị icon người.
            Icon(
                imageVector = Icons.Default.Person, // Icon đại diện khách hàng.
                contentDescription = "Ảnh đại diện ${customer.name}", // Mô tả accessibility.
                tint = Color.White, // Icon màu trắng.
                modifier = Modifier.size(20.dp) // Kích thước icon rõ ràng.
            )
        } else { // Nếu có chữ viết tắt thì hiển thị các ký tự đó.
            Text(
                text = customer.initials, // Ví dụ CL, TQ, BH.
                color = Color.White, // Chữ màu trắng.
                fontSize = 14.sp, // Cỡ chữ lớn.
                fontWeight = FontWeight.Medium // Độ đậm vừa giống ảnh mẫu.
            )
        }
    }
}

@Composable
private fun CustomerQuickActions( // Tạo hàng 4 nút: Zalo, SMS, Gọi, Dẫn đường.
    onZalo: () -> Unit, // Callback của nút Zalo.
    onSms: () -> Unit, // Callback của nút SMS.
    onCall: () -> Unit, // Callback của nút Gọi.
    onNavigate: () -> Unit // Callback của nút Dẫn đường.
) {
    Row( // Xếp bốn nút nằm ngang.
        modifier = Modifier.fillMaxWidth(), // Hàng rộng toàn bộ phần dưới thẻ.
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Tạo khoảng cách giữa các nút.
    ) {
        CustomerActionButton( // Nút thao tác Zalo.
            modifier = Modifier.weight(1.18f), // Rộng hơn nhẹ để đủ chỗ cho icon + chữ.
            label = "Zalo", // Nhãn hiển thị.
            icon = Icons.Default.Chat, // Dùng icon chat làm biểu tượng Zalo dạng demo không cần ảnh asset.
            iconText = "Zalo", // Chữ hiển thị bên trong vùng icon để gần giống logo Zalo.
            onClick = onZalo // Gọi callback khi bấm.
        )
        CustomerActionButton( // Nút thao tác SMS.
            modifier = Modifier.weight(1.08f), // Chia chiều rộng hợp lý cho nút.
            label = "SMS", // Nhãn hiển thị.
            icon = Icons.Default.Sms, // Icon tin nhắn.
            iconText = "SMS", // Chữ hiển thị trong icon demo.
            onClick = onSms // Gọi callback khi bấm.
        )
        CustomerActionButton( // Nút thao tác gọi điện.
            modifier = Modifier.weight(1.0f), // Chia chiều rộng cho nút.
            label = "Gọi", // Nhãn hiển thị.
            icon = Icons.Default.Call, // Icon điện thoại.
            onClick = onCall // Gọi callback khi bấm.
        )
        CustomerActionButton( // Nút dẫn đường.
            modifier = Modifier.weight(1.36f), // Rộng hơn vì nhãn Dẫn đường dài.
            label = "Dẫn đường", // Nhãn hiển thị.
            icon = Icons.Default.LocationOn, // Icon ghim vị trí.
            onClick = onNavigate // Gọi callback khi bấm.
        )
    }
}

@Composable
private fun CustomerActionButton( // Nút thao tác nhỏ dùng chung cho bốn hành động.
    modifier: Modifier = Modifier, // Cho phép truyền weight khác nhau từ bên ngoài.
    label: String, // Nội dung chữ của nút.
    icon: androidx.compose.ui.graphics.vector.ImageVector, // Biểu tượng chính của nút.
    iconText: String? = null, // Chữ nhỏ nằm trong vùng icon, chỉ dùng cho Zalo/SMS.
    onClick: () -> Unit // Hàm chạy khi người dùng bấm nút.
) {
    Box( // Dùng Box để dễ bo góc và căn giữa nội dung.
        modifier = modifier
            .height(36.dp) // Chiều cao nút giống hàng thao tác trong ảnh.
            .clip(RoundedCornerShape(14.dp)) // Bo góc mềm.
            .background(OrangeLight) // Nền xám kem rất nhạt.
            .clickable { onClick() }, // Nhận thao tác chạm.
        contentAlignment = Alignment.Center // Căn giữa hàng icon + chữ.
    ) {
        Row( // Đặt icon và nhãn cạnh nhau.
            modifier = Modifier.padding(horizontal = 5.dp), // Chừa khoảng thở hai bên.
            verticalAlignment = Alignment.CenterVertically // Căn giữa theo chiều dọc.
        ) {
            Box( // Vùng icon hình tròn dùng cho cả bốn nút.
                modifier = Modifier
                    .size(24.dp) // Kích thước vùng icon.
                    .clip(CircleShape) // Tạo hình tròn.
                    .background(Orange), // Dùng màu cam thương hiệu.
                contentAlignment = Alignment.Center // Căn giữa icon/chữ.
            ) {
                if (iconText != null) { // Nếu là Zalo hoặc SMS thì hiển thị chữ nhỏ.
                    Text(
                        text = iconText, // Hiển thị Zalo hoặc SMS.
                        color = Color.White, // Chữ trắng.
                        fontSize = if (iconText == "Zalo") 9.sp else 8.sp, // Thu nhỏ SMS để vừa hình tròn.
                        fontWeight = FontWeight.Bold // Làm chữ dễ đọc.
                    )
                } else { // Các nút còn lại hiển thị icon vector.
                    Icon(
                        imageVector = icon, // Icon được truyền vào.
                        contentDescription = label, // Mô tả accessibility.
                        tint = Color.White, // Icon màu trắng.
                        modifier = Modifier.size(16.dp) // Kích thước icon.
                    )
                }
            }
            Spacer(Modifier.width(4.dp)) // Khoảng cách giữa icon và nhãn.
            Text(
                text = label, // Hiển thị tên thao tác.
                color = if (label == "Dẫn đường") OrangeDark else TextGray, // Làm Dẫn đường nổi bật bằng màu cam.
                fontSize = 11.sp, // Thu nhỏ nhẹ nhãn dài để không tràn.
                fontWeight = FontWeight.SemiBold, // Chữ rõ và dễ đọc.
                maxLines = 1, // Không cho nhãn xuống dòng.
                overflow = TextOverflow.Clip // Không thêm ... trong các nút ngắn.
            )
        }
    }
}

@Composable
private fun CustomerAddButton( // Nút thêm khách hàng nổi.
    modifier: Modifier = Modifier, // Cho phép màn hình cha quyết định vị trí đặt nút.
    onClick: () -> Unit // Hàm chạy khi người dùng bấm dấu cộng.
) {
    Box( // Tạo nút hình tròn.
        modifier = modifier
            .size(56.dp) // Kích thước lớn giống ảnh tham chiếu.
            .clip(CircleShape) // Cắt thành hình tròn.
            .background(Orange) // Dùng màu cam thương hiệu.
            .clickable { onClick() }, // Mở form thêm khách hàng.
        contentAlignment = Alignment.Center // Căn dấu + vào giữa.
    ) {
        Icon(
            imageVector = Icons.Default.Add, // Icon dấu cộng.
            contentDescription = "Thêm khách hàng", // Mô tả accessibility.
            tint = Color.White, // Dấu cộng màu trắng.
            modifier = Modifier.size(34.dp) // Kích thước lớn, dễ nhìn.
        )
    }
}
