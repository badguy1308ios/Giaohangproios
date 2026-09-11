package com.example.giaohangpro

// ======================= ANDROID + OSMDROID IMPORT =======================
// Quyền Internet để tải bản đồ Goong, geocoding và GPS.
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
// Activity và Compose.
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
// Nhúng MapView Android vào Compose.
import androidx.compose.ui.viewinterop.AndroidView
// Foundation và layout.
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
// Material icons và Material3.
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
// Runtime state và lifecycle effect của Compose.
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
// MapLibre hiển thị Goong vector style trong MapView.
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// ================================================================
// 1. MÀU CHỦ ĐẠO
// ================================================================
// Muốn đổi toàn bộ giao diện sang màu khác, chỉnh các hằng số ở đây.
// Màu cam được lấy theo tinh thần hình mẫu người dùng gửi.
// ================================================================
// GOONG GEOCODING
// ================================================================
// API key Goong dùng để tìm tọa độ từ địa chỉ. Tile bản đồ dùng OpenStreetMap
// để tab Bản đồ vẫn hiển thị được khi key Goong hết hạn/bị giới hạn dịch vụ.
// Lưu ý: API key Android luôn có thể bị trích xuất khỏi APK; nên giới hạn key
// theo package/SHA-1 và API/quota trong trang quản trị Goong.
private const val GOONG_API_KEY = "dF8oDtuFE9Vj7R2eYbFv0edCC34U6wwip8wYrcAP"

private val Orange = Color(0xFFC65A22)
private val OrangeDark = Color(0xFFA94318)
private val OrangeLight = Color(0xFFFFF3EC)
private val Blue = Orange
private val Navy = Color(0xFF3E3A37)
private val TextGray = Color(0xFF77716C)
private val Background = Color(0xFFF6F4F1)
private val Border = Color(0xFFDDD8D2)
private val CardWhite = Color.White
private val MoneyGreen = Color(0xFF168A45)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GiaoHangProTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    GiaoHangApp()
                }
            }
        }
    }
}

@Composable
fun GiaoHangProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Orange,
            secondary = Blue,
            background = Background,
            surface = CardWhite,
            onSurface = Navy
        ),
        content = content
    )
}

// ================================================================
// 2. MODEL DỮ LIỆU
// ================================================================
data class Order(
    val code: String,
    val customer: String,
    val phone: String,
    val address: String,
    val item: String,
    val amount: String,
    val tags: List<String>,
    val status: String = "Chưa giao", // Trạng thái đơn hàng.
    val latitude: String = "", // Vĩ độ điểm giao; marker chỉ hiện khi tọa độ hợp lệ.
    val longitude: String = "" // Kinh độ điểm giao; dùng cùng với latitude để dẫn đường.
)

// Mỗi khách hàng có một id ổn định để khi sửa/xóa không bị nhầm khách có cùng tên.
data class Customer(
    val id: Long, // Mã định danh nội bộ của khách hàng.
    val name: String, // Tên chính hiển thị trong danh sách và màn hình chi tiết.
    val phone: String, // Số điện thoại chính để gọi nhanh.
    val address: String, // Địa chỉ giao hàng chính.
    val latitude: String = "", // Vĩ độ của địa chỉ chính, do người dùng chọn trên Goong Map hoặc nhập tay.
    val longitude: String = "", // Kinh độ của địa chỉ chính, do người dùng chọn trên Goong Map hoặc nhập tay.
    val initials: String = "", // Chữ viết tắt hiển thị trong avatar khi không dùng ảnh.
    val aliases: List<String> = emptyList(), // Các tên phụ/tên cửa hàng khác.
    val extraPhones: List<CustomerPhone> = emptyList(), // Các số điện thoại bổ sung.
    val extraAddresses: List<CustomerAddress> = emptyList(), // Các địa chỉ giao hàng bổ sung.
    val note: String = "", // Ghi chú về khách hàng.
    val primaryCanCall: Boolean = true,
    val primaryCanZalo: Boolean = true,
    val primaryCanSms: Boolean = true,
    val photoUri: String = ""
)

// Kiểu liên hệ của một số điện thoại, ví dụ Gọi hoặc Zalo.
data class CustomerPhone(
    val number: String,
    val action: String = "Gọi",
    val canCall: Boolean = true,
    val canZalo: Boolean = true,
    val canSms: Boolean = true
)

// Một địa chỉ có thể kèm tọa độ để sau này nối Google Maps.
data class CustomerAddress(
    val address: String,
    val latitude: String = "",
    val longitude: String = "",
    val isPrimary: Boolean = false
)

private val sampleOrders = listOf(
    Order(
        "GHN-231015-001", "Nguyễn Văn An", "0901234567",
        "123 Đường Lê Lợi, Quận 1, TP. Hồ Chí Minh",
        "Áo thun nam, quần jeans", "1,500,000 đ",
        listOf("GG1P", "GGDH", "COD"),
        latitude = "17.468700", longitude = "106.622000"
    ),
    Order(
        "GHN-231015-002", "Trần Thị Bích", "0987654321",
        "456 Đường Nguyễn Trãi, Quận 5, TP. Hồ Chí Minh",
        "Kem dưỡng da, son môi", "780,000 đ",
        listOf("GG1P", "PTTX", "COD"),
        latitude = "17.468000", longitude = "106.624500"
    ),
    Order(
        "GHN-231015-003", "Nguyễn Văn An", "0901234567",
        "123 Đường Lê Lợi, Quận 1, TP. Hồ Chí Minh",
        "Áo thun nam, quần jeans", "780,000 đ",
        listOf("GG1P", "GGDH", "COD"),
        latitude = "17.468700", longitude = "106.622000"
    ),
    Order(
        "GHN-231015-004", "Lê Văn Cường", "0912345678",
        "25 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
        "Giày thể thao", "950,000 đ",
        listOf("PTTX", "COD"),
        latitude = "17.473800", longitude = "106.617000"
    ),
    Order(
        "GHN-231015-005", "Phạm Thị Dung", "0934567890",
        "88 Điện Biên Phủ, Bình Thạnh, TP. Hồ Chí Minh",
        "Túi xách", "185,000 đ",
        listOf("GGDH", "COD"),
        latitude = "17.472500", longitude = "106.618500"
    )
)

private val sampleCustomers = listOf(
    Customer(
        id = 1L,
        name = "A Dương - 66 Lê Lợi",
        phone = "0905 123 456",
        address = "66 Lê Lợi, TP. Đồng Hới, Quảng Bình",
        latitude = "17.4687",
        longitude = "106.6220",
        initials = "",
        note = "Khách quen"
    ),
    Customer(
        id = 2L,
        name = "Chị Lan - tạp hóa chợ",
        phone = "0912 345 678",
        address = "Chợ Đồng Hới, Quảng Bình",
        latitude = "17.4680",
        longitude = "106.6245",
        initials = "CL"
    ),
    Customer(
        id = 3L,
        name = "Tuấn QS - Nhà ga Quảng Bình",
        phone = "0978 914 054",
        address = "Số 12, đường Trần Hưng Đạo, Đồng Hới, QB",
        latitude = "17.4725",
        longitude = "106.6185",
        initials = "TQ",
        aliases = listOf("A Tuấn Coteccons"),
        extraPhones = listOf(CustomerPhone("0903 112 887", "Zalo")),
        extraAddresses = listOf(
            CustomerAddress("Lô C3, KCN Tân Bình, đường CN13", "10.803863", "106.651189")
        ),
        note = "Giao giờ hành chính, gọi trước 15 phút"
    ),
    Customer(
        id = 4L,
        name = "Bác Hùng - 12 Trần Hưng Đạo",
        phone = "0988 765 432",
        address = "12 Trần Hưng Đạo, Đồng Hới, Quảng Bình",
        latitude = "17.4738",
        longitude = "106.6170",
        initials = "BH"
    ),
    Customer(
        id = 5L,
        name = "Cửa hàng Minh Tâm",
        phone = "0934 567 890",
        address = "Quận 1, TP. Hồ Chí Minh",
        latitude = "10.7769",
        longitude = "106.7009",
        initials = "MT"
    ),
    Customer(
        id = 6L,
        name = "Cô Ba - 89 Lý Thường Kiệt",
        phone = "0909 111 222",
        address = "89 Lý Thường Kiệt",
        initials = "CB"
    )
)

// ================================================================
// 3. APP CHÍNH + 3 TAB
// ================================================================
enum class Tab { MAP, ORDERS, CUSTOMERS } // Ba tab chính của ứng dụng.

// Điều hướng nội bộ đơn giản cho demo: danh sách chính, chi tiết khách và form thêm/sửa.
enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER }

// Xác định cặp textbox nào trong form sẽ nhận tọa độ sau khi người dùng chọn trên bản đồ.
private enum class CoordinateTarget {
    PRIMARY_ADDRESS, // Cập nhật vĩ độ/kinh độ của địa chỉ chính.
    EXTRA_ADDRESS // Cập nhật vĩ độ/kinh độ của địa chỉ phụ.
}

@Composable
fun GiaoHangApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.MAP) }
    var screen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var formIsNew by remember { mutableStateOf(false) }
    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)

    when (screen) {
        AppScreen.MAIN -> Scaffold(
            topBar = { TopHeader(onSettingsClick = { screen = AppScreen.SETTINGS }) },
            bottomBar = { BottomTabs(selected = tab, onSelected = { tab = it }) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Background)) {
                androidx.compose.animation.Crossfade(
                    targetState = tab,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 170),
                    label = "main-tabs"
                ) { activeTab ->
                    when (activeTab) {
                        Tab.MAP -> MapScreen(vm.orders, vm.customers)
                        Tab.ORDERS -> OrderListScreen(vm.orders)
                        Tab.CUSTOMERS -> CustomerListScreen(
                            customers = vm.customers,
                            onCustomerClick = {
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
            }
        }
        AppScreen.CUSTOMER_DETAIL -> {
            val c = selectedCustomer
            if (c == null) screen = AppScreen.MAIN else CustomerDetailScreen(
                customer = c,
                onBack = { screen = AppScreen.MAIN },
                onEdit = { formIsNew = false; screen = AppScreen.CUSTOMER_FORM },
                onPhotoChanged = { uri -> vm.updateCustomer(c.copy(photoUri = uri)) },
                onDelete = { vm.deleteCustomer(c.id); selectedCustomerId = null; screen = AppScreen.MAIN }
            )
        }
        AppScreen.CUSTOMER_FORM -> CustomerFormScreen(
            customer = if (formIsNew) null else selectedCustomer,
            onBack = { screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL },
            onSave = { edited ->
                selectedCustomerId = if (formIsNew) vm.addCustomer(edited) else { vm.updateCustomer(edited); edited.id }
                screen = AppScreen.CUSTOMER_DETAIL
            }
        )
        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })
        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })
        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })
        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })
        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })
        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })
        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER })
        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })
    }
}

// ================================================================
// 4. HEADER
// ================================================================
@Composable
fun TopHeader(onSettingsClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.horizontalGradient(listOf(OrangeDark, Orange))
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color.White, modifier = Modifier.size(19.dp))
        }

        Spacer(Modifier.weight(1f))

        Icon(
            Icons.Default.LocalShipping,
            contentDescription = "Giao Hàng Pro",
            tint = Color.White,
            modifier = Modifier.size(23.dp)
        )

        Spacer(Modifier.width(6.dp))

        Text(
            "Giao Hàng Pro",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("CÀI ĐẶT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingsSection("DỮ LIỆU & ĐỒNG BỘ") {
                SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Sao lưu khách hàng hoặc quản lý đơn hàng") { Toast.makeText(context,"Import / Export dữ liệu",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ, lấy SĐT và xuất CSV từ VTMan") { Toast.makeText(context,"VTMan Export",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { Toast.makeText(context,"Tuyến giao hàng",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
}


private fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)
private fun parseMoney(v: String): Long = v.filter(Char::isDigit).toLongOrNull() ?: 0L

@Composable
private fun MoneyLedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("money_ledger_session", android.content.Context.MODE_PRIVATE) }
    val denoms = listOf(500000L, 200000L, 100000L, 50000L)
    val counts = remember { mutableStateListOf<Int>().apply { denoms.indices.forEach { add(prefs.getInt("count_$it", 0)) } } }
    var bank by remember { mutableStateOf(prefs.getLong("bank", 0L)) }
    var fee by remember { mutableStateOf(prefs.getLong("fee", 0L)) }
    var cod by remember { mutableStateOf(prefs.getLong("cod", 0L)) }
    val cash = denoms.indices.sumOf { denoms[it] * counts[it].toLong() }
    val result = cash + bank - fee - cod
    val resultBg = if (result == 0L) Color(0xFFE6F4EA) else if (result < 0L) Color(0xFFFDE8E7) else Color(0xFFFFECDD)
    val resultColor = if (result == 0L) Color(0xFF2E7D32) else if (result < 0L) Color(0xFFB3261E) else OrangeDark

    LaunchedEffect(counts.toList(), bank, fee, cod) {
        prefs.edit().apply {
            counts.forEachIndexed { i, v -> putInt("count_$i", v) }
            putLong("bank", bank); putLong("fee", fee); putLong("cod", cod)
        }.apply()
    }

    fun resetAll() {
        counts.indices.forEach { counts[it] = 0 }
        bank = 0L; fee = 0L; cod = 0L
        prefs.edit().clear().apply()
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("BẢNG KÊ TIỀN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đóng bảng kê", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = resultBg)) {
                Column(Modifier.padding(10.dp)) {
                    Text("KẾT QUẢ BẢNG TÍNH", color = resultColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text((if (result > 0) "+" else "") + fmtMoney(result) + "đ", color = resultColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("(Tiền mặt + Bank) - (Cước + COD)", color = TextGray, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ĐẾM TIỀN MẶT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { resetAll() }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(16.dp)) { Text("Reset 0", fontSize = 12.sp) }
            }

            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    denoms.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtMoney(d) + "đ", modifier = Modifier.width(84.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (counts[i] > 0) counts[i]-- }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, "Bớt", tint = Orange, modifier = Modifier.size(19.dp)) }
                            OutlinedTextField(value = if (counts[i] == 0) "" else counts[i].toString(), onValueChange = { counts[i] = it.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(9999) ?: 0 }, modifier = Modifier.width(62.dp).height(48.dp), singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(38.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(21.dp)) } }
                            Text(fmtMoney(d * counts[i].toLong()) + "đ", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TỔNG TIỀN MẶT", modifier = Modifier.weight(1f), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMoney(cash) + "đ", color = OrangeDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("ĐỐI SOÁT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyEntryRow("Bank", bank) { bank = it }
                    MoneyEntryRow("Cước", fee) { fee = it }
                    MoneyEntryRow("COD", cod) { cod = it }
                }
            }
        }
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = if (value == 0L) "" else fmtMoney(value), onValueChange = { onValueChange(parseMoney(it)) }, modifier = Modifier.weight(1f).height(50.dp), singleLine = true, placeholder = { Text("0", fontSize = 13.sp) }, suffix = { Text("đ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp, top = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 48.dp else 56.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(OrangeLight), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = TextGray, maxLines = 2, lineHeight = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = Border) }


@Composable
private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("CÀI ĐẶT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingsSection("DỮ LIỆU & ĐỒNG BỘ") {
                SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Sao lưu khách hàng hoặc quản lý đơn hàng") { Toast.makeText(context,"Import / Export dữ liệu",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ, lấy SĐT và xuất CSV từ VTMan") { Toast.makeText(context,"VTMan Export",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { Toast.makeText(context,"Tuyến giao hàng",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
}


private fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)
private fun parseMoney(v: String): Long = v.filter(Char::isDigit).toLongOrNull() ?: 0L

@Composable
private fun MoneyLedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("money_ledger_session", android.content.Context.MODE_PRIVATE) }
    val denoms = listOf(500000L, 200000L, 100000L, 50000L)
    val counts = remember { mutableStateListOf<Int>().apply { denoms.indices.forEach { add(prefs.getInt("count_$it", 0)) } } }
    var bank by remember { mutableStateOf(prefs.getLong("bank", 0L)) }
    var fee by remember { mutableStateOf(prefs.getLong("fee", 0L)) }
    var cod by remember { mutableStateOf(prefs.getLong("cod", 0L)) }
    val cash = denoms.indices.sumOf { denoms[it] * counts[it].toLong() }
    val result = cash + bank - fee - cod
    val resultBg = if (result == 0L) Color(0xFFE6F4EA) else if (result < 0L) Color(0xFFFDE8E7) else Color(0xFFFFECDD)
    val resultColor = if (result == 0L) Color(0xFF2E7D32) else if (result < 0L) Color(0xFFB3261E) else OrangeDark

    LaunchedEffect(counts.toList(), bank, fee, cod) {
        prefs.edit().apply {
            counts.forEachIndexed { i, v -> putInt("count_$i", v) }
            putLong("bank", bank); putLong("fee", fee); putLong("cod", cod)
        }.apply()
    }

    fun resetAll() {
        counts.indices.forEach { counts[it] = 0 }
        bank = 0L; fee = 0L; cod = 0L
        prefs.edit().clear().apply()
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("BẢNG KÊ TIỀN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đóng bảng kê", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = resultBg)) {
                Column(Modifier.padding(10.dp)) {
                    Text("KẾT QUẢ BẢNG TÍNH", color = resultColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text((if (result > 0) "+" else "") + fmtMoney(result) + "đ", color = resultColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("(Tiền mặt + Bank) - (Cước + COD)", color = TextGray, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ĐẾM TIỀN MẶT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { resetAll() }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(16.dp)) { Text("Reset 0", fontSize = 12.sp) }
            }

            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    denoms.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtMoney(d) + "đ", modifier = Modifier.width(84.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (counts[i] > 0) counts[i]-- }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, "Bớt", tint = Orange, modifier = Modifier.size(19.dp)) }
                            OutlinedTextField(value = if (counts[i] == 0) "" else counts[i].toString(), onValueChange = { counts[i] = it.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(9999) ?: 0 }, modifier = Modifier.width(62.dp).height(48.dp), singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(38.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(21.dp)) } }
                            Text(fmtMoney(d * counts[i].toLong()) + "đ", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TỔNG TIỀN MẶT", modifier = Modifier.weight(1f), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMoney(cash) + "đ", color = OrangeDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("ĐỐI SOÁT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyEntryRow("Bank", bank) { bank = it }
                    MoneyEntryRow("Cước", fee) { fee = it }
                    MoneyEntryRow("COD", cod) { cod = it }
                }
            }
        }
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = if (value == 0L) "" else fmtMoney(value), onValueChange = { onValueChange(parseMoney(it)) }, modifier = Modifier.weight(1f).height(50.dp), singleLine = true, placeholder = { Text("0", fontSize = 13.sp) }, suffix = { Text("đ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp, top = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 48.dp else 56.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(OrangeLight), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = TextGray, maxLines = 2, lineHeight = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = Border) }


@Composable
private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("CÀI ĐẶT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingsSection("DỮ LIỆU & ĐỒNG BỘ") {
                SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Sao lưu khách hàng hoặc quản lý đơn hàng") { Toast.makeText(context,"Import / Export dữ liệu",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ, lấy SĐT và xuất CSV từ VTMan") { Toast.makeText(context,"VTMan Export",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { Toast.makeText(context,"Tuyến giao hàng",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
}


private fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)
private fun parseMoney(v: String): Long = v.filter(Char::isDigit).toLongOrNull() ?: 0L

@Composable
private fun MoneyLedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("money_ledger_session", android.content.Context.MODE_PRIVATE) }
    val denoms = listOf(500000L, 200000L, 100000L, 50000L)
    val counts = remember { mutableStateListOf<Int>().apply { denoms.indices.forEach { add(prefs.getInt("count_$it", 0)) } } }
    var bank by remember { mutableStateOf(prefs.getLong("bank", 0L)) }
    var fee by remember { mutableStateOf(prefs.getLong("fee", 0L)) }
    var cod by remember { mutableStateOf(prefs.getLong("cod", 0L)) }
    val cash = denoms.indices.sumOf { denoms[it] * counts[it].toLong() }
    val result = cash + bank - fee - cod
    val resultBg = if (result == 0L) Color(0xFFE6F4EA) else if (result < 0L) Color(0xFFFDE8E7) else Color(0xFFFFECDD)
    val resultColor = if (result == 0L) Color(0xFF2E7D32) else if (result < 0L) Color(0xFFB3261E) else OrangeDark

    LaunchedEffect(counts.toList(), bank, fee, cod) {
        prefs.edit().apply {
            counts.forEachIndexed { i, v -> putInt("count_$i", v) }
            putLong("bank", bank); putLong("fee", fee); putLong("cod", cod)
        }.apply()
    }

    fun resetAll() {
        counts.indices.forEach { counts[it] = 0 }
        bank = 0L; fee = 0L; cod = 0L
        prefs.edit().clear().apply()
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("BẢNG KÊ TIỀN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đóng bảng kê", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = resultBg)) {
                Column(Modifier.padding(10.dp)) {
                    Text("KẾT QUẢ BẢNG TÍNH", color = resultColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text((if (result > 0) "+" else "") + fmtMoney(result) + "đ", color = resultColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("(Tiền mặt + Bank) - (Cước + COD)", color = TextGray, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ĐẾM TIỀN MẶT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { resetAll() }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(16.dp)) { Text("Reset 0", fontSize = 12.sp) }
            }

            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    denoms.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtMoney(d) + "đ", modifier = Modifier.width(84.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (counts[i] > 0) counts[i]-- }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, "Bớt", tint = Orange, modifier = Modifier.size(19.dp)) }
                            OutlinedTextField(value = if (counts[i] == 0) "" else counts[i].toString(), onValueChange = { counts[i] = it.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(9999) ?: 0 }, modifier = Modifier.width(62.dp).height(48.dp), singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(38.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(21.dp)) } }
                            Text(fmtMoney(d * counts[i].toLong()) + "đ", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TỔNG TIỀN MẶT", modifier = Modifier.weight(1f), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMoney(cash) + "đ", color = OrangeDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("ĐỐI SOÁT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyEntryRow("Bank", bank) { bank = it }
                    MoneyEntryRow("Cước", fee) { fee = it }
                    MoneyEntryRow("COD", cod) { cod = it }
                }
            }
        }
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = if (value == 0L) "" else fmtMoney(value), onValueChange = { onValueChange(parseMoney(it)) }, modifier = Modifier.weight(1f).height(50.dp), singleLine = true, placeholder = { Text("0", fontSize = 13.sp) }, suffix = { Text("đ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp, top = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 48.dp else 56.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(OrangeLight), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = TextGray, maxLines = 2, lineHeight = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = Border) }


@Composable
private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("CÀI ĐẶT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingsSection("DỮ LIỆU & ĐỒNG BỘ") {
                SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Sao lưu khách hàng hoặc quản lý đơn hàng") { Toast.makeText(context,"Import / Export dữ liệu",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ, lấy SĐT và xuất CSV từ VTMan") { Toast.makeText(context,"VTMan Export",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { Toast.makeText(context,"Tuyến giao hàng",Toast.LENGTH_SHORT).show() }
            }
            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }
}


private fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)
private fun parseMoney(v: String): Long = v.filter(Char::isDigit).toLongOrNull() ?: 0L

@Composable
private fun MoneyLedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("money_ledger_session", android.content.Context.MODE_PRIVATE) }
    val denoms = listOf(500000L, 200000L, 100000L, 50000L)
    val counts = remember { mutableStateListOf<Int>().apply { denoms.indices.forEach { add(prefs.getInt("count_$it", 0)) } } }
    var bank by remember { mutableStateOf(prefs.getLong("bank", 0L)) }
    var fee by remember { mutableStateOf(prefs.getLong("fee", 0L)) }
    var cod by remember { mutableStateOf(prefs.getLong("cod", 0L)) }
    val cash = denoms.indices.sumOf { denoms[it] * counts[it].toLong() }
    val result = cash + bank - fee - cod
    val resultBg = if (result == 0L) Color(0xFFE6F4EA) else if (result < 0L) Color(0xFFFDE8E7) else Color(0xFFFFECDD)
    val resultColor = if (result == 0L) Color(0xFF2E7D32) else if (result < 0L) Color(0xFFB3261E) else OrangeDark

    LaunchedEffect(counts.toList(), bank, fee, cod) {
        prefs.edit().apply {
            counts.forEachIndexed { i, v -> putInt("count_$i", v) }
            putLong("bank", bank); putLong("fee", fee); putLong("cod", cod)
        }.apply()
    }

    fun resetAll() {
        counts.indices.forEach { counts[it] = 0 }
        bank = 0L; fee = 0L; cod = 0L
        prefs.edit().clear().apply()
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("BẢNG KÊ TIỀN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp).height(42.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đóng bảng kê", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Background).verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = resultBg)) {
                Column(Modifier.padding(10.dp)) {
                    Text("KẾT QUẢ BẢNG TÍNH", color = resultColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text((if (result > 0) "+" else "") + fmtMoney(result) + "đ", color = resultColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("(Tiền mặt + Bank) - (Cước + COD)", color = TextGray, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ĐẾM TIỀN MẶT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { resetAll() }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(16.dp)) { Text("Reset 0", fontSize = 12.sp) }
            }

            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    denoms.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtMoney(d) + "đ", modifier = Modifier.width(84.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (counts[i] > 0) counts[i]-- }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, "Bớt", tint = Orange, modifier = Modifier.size(19.dp)) }
                            OutlinedTextField(value = if (counts[i] == 0) "" else counts[i].toString(), onValueChange = { counts[i] = it.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(9999) ?: 0 }, modifier = Modifier.width(62.dp).height(48.dp), singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(38.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(21.dp)) } }
                            Text(fmtMoney(d * counts[i].toLong()) + "đ", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TỔNG TIỀN MẶT", modifier = Modifier.weight(1f), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(fmtMoney(cash) + "đ", color = OrangeDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("ĐỐI SOÁT", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyEntryRow("Bank", bank) { bank = it }
                    MoneyEntryRow("Cước", fee) { fee = it }
                    MoneyEntryRow("COD", cod) { cod = it }
                }
            }
        }
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = if (value == 0L) "" else fmtMoney(value), onValueChange = { onValueChange(parseMoney(it)) }, modifier = Modifier.weight(1f).height(50.dp), singleLine = true, placeholder = { Text("0", fontSize = 13.sp) }, suffix = { Text("đ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp, top = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 48.dp else 56.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(OrangeLight), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = TextGray, maxLines = 2, lineHeight = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = Border) }

private val DEFAULT_MAP_POINT = MapPoint(17.4689, 106.6220) // Đồng Hới, Quảng Bình.

// Chuyển text Latitude/Longitude thành MapPoint an toàn; dữ liệu sai sẽ trả null.
private fun pointFromStrings(latitude: String, longitude: String): MapPoint? {
    val lat = latitude.trim().toDoubleOrNull() ?: return null // Không phải số thì không tạo marker.
    val lng = longitude.trim().toDoubleOrNull() ?: return null // Không phải số thì không tạo marker.
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null // Kiểm tra phạm vi tọa độ hợp lệ.
    return MapPoint(lat, lng) // Trả về điểm hợp lệ.
}

// Lấy vị trí hiện tại bằng Android LocationManager, không cần Google Play Services hoặc API key.
@Composable
private fun rememberDriverLocation(): State<MapPoint?> {
    val context = LocalContext.current // Context để kiểm tra quyền và lấy LocationManager.
    val locationState = remember { mutableStateOf<MapPoint?>(null) } // State GPS để Compose tự cập nhật UI.
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Có quyền chính xác hoặc gần đúng đều cho phép lấy vị trí khi hệ thống hỗ trợ.
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Khi chưa có quyền, chỉ yêu cầu một lần; nếu người dùng từ chối bản đồ vẫn hoạt động bình thường.
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Đăng ký cập nhật GPS chỉ khi composable đang tồn tại và quyền đã được cấp.
    DisposableEffect(context, hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val listener = android.location.LocationListener { location ->
            locationState.value = MapPoint(location.latitude, location.longitude) // Cập nhật marker tài xế.
        }
        try {
            // Lấy ngay vị trí cuối cùng để bản đồ có dữ liệu nhanh trước khi GPS cập nhật mới.
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.firstNotNullOfOrNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }?.let { locationState.value = MapPoint(it.latitude, it.longitude) }
            // Cập nhật tối đa mỗi 10 giây hoặc khi di chuyển khoảng 15 m để giảm hao pin.
            providers.forEach { provider ->
                runCatching { manager.requestLocationUpdates(provider, 10_000L, 15f, listener, Looper.getMainLooper()) }
            }
        } catch (_: SecurityException) {
            // Quyền có thể bị thu hồi trong lúc chạy; giữ bản đồ hoạt động thay vì crash.
        }
        onDispose {
            // Rời tab/màn hình thì dừng GPS để tránh hao pin và tránh giữ reference Activity.
            runCatching { manager.removeUpdates(listener) }
        }
    }
    return locationState // Trả State để nơi gọi đọc bằng .value.
}

// Mở Google Maps bên ngoài bằng Intent navigation URI; ứng dụng không tự tính route.
// Gọi Goong Geocoding để lấy tọa độ WGS84 từ địa chỉ khách hàng.
private suspend fun geocodeAddressWithGoong(address: String): MapPoint? = withContext(Dispatchers.IO) {
    if (address.isBlank()) return@withContext null
    val encoded = URLEncoder.encode(address.trim(), "UTF-8")
    val url = URL("https://rsapi.goong.io/Geocode?address=$encoded&api_key=$GOONG_API_KEY")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
        setRequestProperty("Accept", "application/json")
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val location = results.optJSONObject(0)
            ?.optJSONObject("geometry")
            ?.optJSONObject("location")
            ?: return@withContext null
        val lat = location.optDouble("lat", Double.NaN)
        val lng = location.optDouble("lng", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) null else pointFromStrings(lat.toString(), lng.toString())
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun openGoogleNavigation(context: android.content.Context, point: MapPoint) {
    val uri = android.net.Uri.parse("google.navigation:q=${point.latitude},${point.longitude}&mode=l") // mode=l ưu tiên chế độ xe máy.
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps") // Ưu tiên mở đúng ứng dụng Google Maps.
    }
    try {
        context.startActivity(intent) // Mở Google Maps nếu thiết bị đã cài.
    } catch (_: android.content.ActivityNotFoundException) {
        // Fallback: bỏ package để app bản đồ/trình duyệt tương thích có thể xử lý URI.
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "Thiết bị chưa có ứng dụng hỗ trợ dẫn đường.", Toast.LENGTH_LONG).show()
        }
    }
}

// Tab bản đồ hiển thị tất cả đơn hàng bằng marker kiểu bong bóng có đánh số thứ tự.
// Nếu đơn hàng/khách chưa có tọa độ, marker vẫn xuất hiện tạm tại vị trí GPS tài xế để người dùng biết đơn đó đang chờ bổ sung tọa độ.
@Composable
fun MapScreen(orders: List<Order>, customers: List<Customer>) {
    val context = LocalContext.current
    val driverLocation by rememberDriverLocation()
    var selectedMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var mapExpanded by remember { mutableStateOf(false) }

    val mappedOrders = remember(orders, customers, driverLocation) {
        orders.mapIndexed { index, order ->
            val orderPoint = pointFromStrings(order.latitude, order.longitude)
            val customerPoint = customers.firstOrNull {
                it.phone.filter(Char::isDigit) == order.phone.filter(Char::isDigit) ||
                    it.name.equals(order.customer, ignoreCase = true) ||
                    it.address.equals(order.address, ignoreCase = true)
            }?.let { pointFromStrings(it.latitude, it.longitude) }
            val realPoint = orderPoint ?: customerPoint
            val displayPoint = realPoint ?: driverLocation ?: DEFAULT_MAP_POINT
            MapOrderMarker(order, displayPoint, index + 1, realPoint != null)
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        TopHeader()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GoongOrderMap(
                modifier = Modifier.fillMaxSize(),
                orders = mappedOrders,
                driverLocation = driverLocation,
                selectedOrderNumber = selectedMarker?.number,
                expanded = mapExpanded,
                onToggleExpand = { mapExpanded = !mapExpanded },
                onOrderSelected = { selectedMarker = it }
            )

            if (!mapExpanded) {
                MapOrderBottomSheet(
                    orders = mappedOrders,
                    selectedNumber = selectedMarker?.number,
                    onOrderClick = { selectedMarker = it },
                    onNavigate = { marker ->
                        if (marker.hasRealCoordinate) {
                            openGoogleNavigation(context, marker.point)
                        } else {
                            Toast.makeText(context, "Đơn này chưa có tọa độ. Hãy bổ sung trong Khách hàng.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

// Bottom sheet dạng danh sách đơn hàng giống hình tham chiếu: số thứ tự, nút dẫn đường, mã đơn, tên khách và số tiền.
@Composable
private fun BoxScope.MapOrderBottomSheet(
    orders: List<MapOrderMarker>, // Danh sách đơn đã gắn số thứ tự marker.
    selectedNumber: Int?, // Số thứ tự đang được chọn để tô nổi dòng tương ứng.
    onOrderClick: (MapOrderMarker) -> Unit, // Bấm dòng để focus marker trên bản đồ.
    onNavigate: (MapOrderMarker) -> Unit // Bấm biểu tượng dẫn đường để mở Google Maps.
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        color = Color.White.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 185.dp, max = 250.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Tay nắm nhỏ giúp giao diện có cảm giác bottom sheet giống hình mẫu.
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF9BA8B8))
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Orange, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${orders.size} đơn hàng",
                    color = Navy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Thu gọn danh sách", tint = Orange, modifier = Modifier.size(26.dp))
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(orders, key = { it.order.code + it.number }) { marker ->
                    MapOrderListRow(
                        marker = marker,
                        selected = marker.number == selectedNumber,
                        onClick = { onOrderClick(marker) },
                        onNavigate = { onNavigate(marker) }
                    )
                }
            }
        }
    }
}

// Một dòng đơn hàng trong danh sách tab Bản đồ.
@Composable
private fun MapOrderListRow(
    marker: MapOrderMarker,
    selected: Boolean,
    onClick: () -> Unit,
    onNavigate: () -> Unit
) {
    val order = marker.order
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) OrangeLight else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberCircle(marker.number, selected = selected)
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (marker.hasRealCoordinate) Blue else Color(0xFFB6C0CC))
                    .clickable(enabled = marker.hasRealCoordinate) { onNavigate() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "Dẫn đường",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(order.customer, color = TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!marker.hasRealCoordinate) {
                    Text("Chưa có tọa độ", color = OrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(order.amount, color = MoneyGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = "Xem đơn", tint = TextGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Tạo Drawable marker kiểu bong bóng bằng Canvas Android để OSMDroid có thể hiển thị số thứ tự ở giữa.
private fun createNumberBubbleDrawable(
    context: android.content.Context, // Context dùng để lấy density màn hình.
    number: Int, // Số thứ tự sẽ được vẽ trong bong bóng.
    isPending: Boolean // true = đơn chưa có tọa độ thật, dùng màu xám để phân biệt với điểm giao thật.
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density // Quy đổi dp sang pixel.
    val width = (34 * density).toInt() // Chiều rộng bong bóng.
    val height = (42 * density).toInt() // Chiều cao gồm thân và mũi nhọn.
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888) // Tạo vùng ảnh trong suốt.
    val canvas = android.graphics.Canvas(bitmap) // Canvas Android để tự vẽ marker.
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) // Bật khử răng cưa cho hình tròn/chữ.
    val bubbleColor = if (isPending) android.graphics.Color.rgb(117, 132, 153) else android.graphics.Color.rgb(227, 75, 10) // Xám = chờ tọa độ, cam = tọa độ thật.
    paint.color = bubbleColor // Áp dụng màu nền bong bóng.

    val bodyBottom = (32 * density) // Đáy phần thân trước mũi nhọn.
    val radius = (12 * density) // Bán kính bo góc.
    val rect = android.graphics.RectF(0f, 0f, width.toFloat(), bodyBottom) // Khung phần thân bong bóng.
    canvas.drawRoundRect(rect, radius, radius, paint) // Vẽ thân bo tròn.

    val pointer = android.graphics.Path().apply { // Tạo mũi nhọn chỉ xuống vị trí tọa độ.
        moveTo(width / 2f - 6 * density, bodyBottom)
        lineTo(width / 2f, height.toFloat())
        lineTo(width / 2f + 6 * density, bodyBottom)
        close()
    }
    canvas.drawPath(pointer, paint) // Vẽ mũi nhọn cùng màu thân.

    paint.color = android.graphics.Color.WHITE // Số thứ tự màu trắng để dễ đọc.
    paint.textSize = 14 * density // Kích thước chữ.
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD // Chữ đậm.
    paint.textAlign = android.graphics.Paint.Align.CENTER // Căn giữa theo chiều ngang.
    val textY = bodyBottom / 2f - (paint.ascent() + paint.descent()) / 2f // Căn giữa theo chiều dọc.
    canvas.drawText(number.toString(), width / 2f, textY, paint) // Vẽ số thứ tự vào giữa bong bóng.

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap) // Trả về Drawable cho OSMDroid Marker.icon.
}

private fun createDriverMotorbikeBitmap(context: android.content.Context): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.rgb(22, 141, 226)
    canvas.drawCircle(size / 2f, size / 2f, size * 0.48f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 19 * density
    paint.textAlign = android.graphics.Paint.Align.CENTER
    val y = size / 2f - (paint.ascent() + paint.descent()) / 2f
    canvas.drawText("🛵", size / 2f, y, paint)
    return bitmap
}


// ================================================================
// 5. TAB BẢN ĐỒ - GOONG VECTOR STYLE + MAPLIBRE
// ================================================================
// Runtime này lấy nguyên cấu hình từ app giaohang-goong v3.04:
// MapLibre đọc style vector trong res/raw/goong_map_style.json.
// File style chứa các URL base.json/goong.json, sprite và glyphs cùng Goong key.
data class MapPoint(
    val latitude: Double,
    val longitude: Double
)

data class MapOrderMarker(
    val order: Order,
    val point: MapPoint,
    val number: Int,
    val hasRealCoordinate: Boolean
)

private fun goongStyle(context: android.content.Context): String =
    context.resources.openRawResource(com.example.giaohangpro.R.raw.goong_map_style)
        .bufferedReader().use { it.readText() }

@Composable
private fun GoongOrderMap(
    modifier: Modifier,
    orders: List<MapOrderMarker>,
    driverLocation: MapPoint?,
    selectedOrderNumber: Int?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOrderSelected: (MapOrderMarker) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { readyMap ->
                        readyMap.setTileCacheEnabled(true)
                        readyMap.setStyle(Style.Builder().fromJson(goongStyle(context))) {
                            map = readyMap
                            readyMap.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(DEFAULT_MAP_POINT.latitude, DEFAULT_MAP_POINT.longitude))
                                .zoom(14.0).build()
                            readyMap.setOnMarkerClickListener { clicked ->
                                val number = clicked.title?.substringAfter("Đơn #")?.substringBefore(" ")?.toIntOrNull()
                                orders.firstOrNull { it.number == number }?.let(onOrderSelected)
                                false
                            }
                        }
                    }
                }
            },
            update = { view -> view.getMapAsync { readyMap -> if (readyMap.style != null) map = readyMap } }
        )

        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapControlButton(Icons.Default.MyLocation, "Vị trí của tôi") {
                driverLocation?.let { point ->
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
                }
            }
            MapControlButton(Icons.Default.Add, "Phóng to") {
                map?.animateCamera(CameraUpdateFactory.zoomBy(1.0))
            }
            MapControlButton(Icons.Default.Remove, "Thu nhỏ") {
                map?.animateCamera(CameraUpdateFactory.zoomBy(-1.0))
            }
            MapControlButton(if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Mở rộng bản đồ") {
                onToggleExpand()
            }
        }
    }

    LaunchedEffect(map, orders, driverLocation, selectedOrderNumber) {
        map?.let { readyMap ->
            readyMap.clear()
            driverLocation?.let { point ->
                val driverIcon = org.maplibre.android.annotations.IconFactory.getInstance(context)
                    .fromBitmap(createDriverMotorbikeBitmap(context))
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(point.latitude, point.longitude))
                        .icon(driverIcon)
                        .title("🛵 Vị trí hiện tại của tài xế")
                        .snippet("GPS đang cập nhật")
                )
            }
            orders.forEach { markerData ->
                val order = markerData.order
                val numberBitmap = (createNumberBubbleDrawable(context, markerData.number, !markerData.hasRealCoordinate) as android.graphics.drawable.BitmapDrawable).bitmap
                val numberIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(numberBitmap)
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(markerData.point.latitude, markerData.point.longitude))
                        .icon(numberIcon)
                        .title("Đơn #${markerData.number} • ${order.code}")
                        .snippet(if (markerData.hasRealCoordinate) order.address else "Chưa có tọa độ giao hàng")
                )
            }
            selectedOrderNumber?.let { number ->
                orders.firstOrNull { it.number == number }?.let { selected ->
                    readyMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(selected.point.latitude, selected.point.longitude),
                            if (selected.hasRealCoordinate) 16.0 else 15.0
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(34.dp).clickable { onClick() },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 4.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Navy, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CustomerCoordinateMapPicker(
    initialPoint: MapPoint?,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driverLocation by rememberDriverLocation()
    var selectedPoint by remember(initialPoint) { mutableStateOf(initialPoint) }
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(Modifier.fillMaxSize()) {
                CustomerPageHeader(title = "CHỌN TỌA ĐỘ TRÊN BẢN ĐỒ", onBack = onDismiss)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            mapView.apply {
                                getMapAsync { readyMap ->
                                    readyMap.setStyle(Style.Builder().fromJson(goongStyle(context))) {
                                        map = readyMap
                                        val point = selectedPoint ?: driverLocation ?: DEFAULT_MAP_POINT
                                        readyMap.cameraPosition = CameraPosition.Builder()
                                            .target(LatLng(point.latitude, point.longitude)).zoom(15.0).build()
                                        readyMap.addMarker(MarkerOptions().position(LatLng(point.latitude, point.longitude)).title("Vị trí đang chọn"))
                                        readyMap.addOnMapClickListener { tapped ->
                                            selectedPoint = MapPoint(tapped.latitude, tapped.longitude)
                                            readyMap.clear()
                                            readyMap.addMarker(MarkerOptions().position(tapped).title("Vị trí đang chọn"))
                                            true
                                        }
                                    }
                                }
                            }
                        },
                        update = { view -> view.getMapAsync { readyMap -> if (readyMap.style != null) map = readyMap } }
                    )
                    selectedPoint?.let { point ->
                        Card(
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                        ) {
                            Text(
                                "Vĩ độ: %.6f\nKinh độ: %.6f".format(java.util.Locale.US, point.latitude, point.longitude),
                                modifier = Modifier.padding(12.dp), color = Navy
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text("HỦY", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { selectedPoint?.let(onSavePoint) },
                        enabled = selectedPoint != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) { Text("LƯU", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ================================================================
// 6. TAB CHI TIẾT ĐƠN
// ================================================================
@Composable
fun OrderListScreen(orders: List<Order>) {
    var keyword by remember { mutableStateOf("") }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword = it }
    }
    val filteredOrders = remember(orders, keyword) {
        val q = keyword.trim()
        if (q.isBlank()) orders else orders.filter {
            it.code.contains(q, true) || it.customer.contains(q, true) ||
                it.phone.contains(q, true) || it.address.contains(q, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopHeader()
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Spacer(Modifier.height(6.dp))
            SearchBox(keyword, { keyword = it }, { keyword = "" }) {
                scanLauncher.launch(ScanOptions().apply {
                    // Quét cả QR và các mã vạch 1D phổ biến (Code 128, EAN, UPC, Code 39...).
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")
                    setBeepEnabled(false)
                    // Dùng CaptureActivity riêng và khóa dọc để camera không xoay ngang.
                    setCaptureActivity(PortraitCaptureActivity::class.java)
                    setOrientationLocked(true)
                    setBarcodeImageEnabled(false)
                })
            }
            Spacer(Modifier.height(6.dp))
            Text("Tổng số: ${filteredOrders.size} đơn", color=Navy, fontSize=20.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp), contentPadding=PaddingValues(bottom=8.dp)) {
                itemsIndexed(filteredOrders) { index, order -> OrderCard(index+1, order) }
            }
        }
    }
}

@Composable
fun OrderCard(index: Int, order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberCircle(index, selected = true)

                Spacer(Modifier.width(8.dp))

                Text(
                    order.code,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    "TT505",
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))

                                Text(
                    order.amount,
                    color = MoneyGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))

            OrderInfoRow(Icons.Default.Store, order.customer)
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}")
            OrderInfoRow(Icons.Default.LocationOn, order.address)
            OrderInfoRow(Icons.Default.Inventory2, order.item)

            Spacer(Modifier.height(5.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                order.tags.forEach { Tag(it) }
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true)
                ActionButton("Bank", Icons.Default.AccountBalance)
                ActionButton("Zalo", Icons.Default.Chat)
                ActionButton("SMS", Icons.Default.Sms)
                ActionButton("Gọi", Icons.Default.Call)
            }
        }
    }
}

@Composable
fun OrderInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            color = Navy,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Tag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEDE9E4))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = Navy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RowScope.ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean = false
) {
    Box(
        Modifier
            .height(36.dp)
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Orange else OrangeLight)
            .border(1.dp, Orange, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (filled) Color.White else OrangeDark,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                color = if (filled) Color.White else Navy,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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

    val filteredCustomers = customers.filter { customer -> // Tạo danh sách mới chỉ gồm các khách phù hợp từ khóa.
        val query = keyword.trim() // Xóa khoảng trắng thừa ở đầu và cuối từ khóa.
        query.isBlank() || // Nếu chưa nhập gì thì giữ nguyên toàn bộ danh sách.
            customer.name.contains(query, ignoreCase = true) || // Cho phép tìm theo tên, không phân biệt hoa/thường.
            customer.phone.contains(query, ignoreCase = true) || // Cho phép tìm theo số điện thoại.
            customer.address.contains(query, ignoreCase = true) // Cho phép tìm theo địa chỉ.
    }

    Box( // Dùng Box để nút + có thể nổi đè lên danh sách ở góc dưới.
        modifier = Modifier
            .fillMaxSize() // Chiếm toàn bộ vùng nội dung của tab.
            .background(Background) // Đặt màu nền xám xanh nhạt cho toàn màn hình.
    ) {
        Column(Modifier.fillMaxSize()) { // Xếp header, ô tìm kiếm và danh sách theo chiều dọc.
            TopHeader() // Hiển thị thanh tiêu đề màu cam giống ảnh mẫu.

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
                text = "Tìm tên, SĐT, địa chỉ", // Đúng nội dung gợi ý theo yêu cầu UI.
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


// ================================================================
// 8. CHI TIẾT KHÁCH HÀNG
// ================================================================
@Composable
fun CustomerDetailScreen(
    customer: Customer,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPhotoChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        onPhotoChanged(uri.toString())
    }

    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCameraUri?.let { onPhotoChanged(it.toString()) }
    }

    fun phoneFor(kind: String): String? {
        val primaryOk = when (kind) {
            "call" -> customer.primaryCanCall
            "zalo" -> customer.primaryCanZalo
            else -> customer.primaryCanSms
        }
        if (primaryOk && customer.phone.isNotBlank()) return customer.phone
        return customer.extraPhones.firstOrNull {
            when (kind) { "call" -> it.canCall; "zalo" -> it.canZalo; else -> it.canSms }
        }?.number
    }
    fun launchPhone(kind: String) {
        val raw = phoneFor(kind) ?: return
        val number = raw.filter(Char::isDigit)
        val uri = when (kind) {
            "call" -> android.net.Uri.parse("tel:$number")
            "zalo" -> android.net.Uri.parse("https://zalo.me/$number")
            else -> android.net.Uri.parse("smsto:$number")
        }
        val action = if (kind == "call") android.content.Intent.ACTION_DIAL else android.content.Intent.ACTION_VIEW
        runCatching { context.startActivity(android.content.Intent(action, uri)) }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("CHI TIẾT KHÁCH HÀNG", onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CustomerDetailContent(
                customer = customer,
                modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
            )
            CustomerSideActions(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 312.dp, bottom = 3.dp),
                onCall = { launchPhone("call") },
                onZalo = { launchPhone("zalo") },
                onSms = { launchPhone("sms") },
                onNavigate = {
                    pointFromStrings(customer.latitude, customer.longitude)?.let { openGoogleNavigation(context, it) }
                },
                onEdit = onEdit,
                onDelete = { showDeleteDialog = true }
            )
        }
    }

    if (showPhotoMenu) AlertDialog(
        onDismissRequest = { showPhotoMenu = false },
        title = { Text("Ảnh cổng nhà khách") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { showPhotoMenu = false; galleryLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text("Chọn từ thư viện")
                }
                FilledTonalButton(
                    onClick = {
                        showPhotoMenu = false
                        val file = java.io.File(context.filesDir, "customer_gate_${customer.id}_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text("Chụp ảnh mới")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showPhotoMenu = false }) { Text("Hủy") } }
    )

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Xóa khách hàng") },
        text = { Text("Bạn có chắc muốn xóa ${customer.name} không?") },
        confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Xóa", color = Color(0xFFE21B1B)) } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") } }
    )
}

@Composable
private fun CustomerPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(Brush.horizontalGradient(listOf(OrangeDark, Orange))).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(3.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CustomerSideActions(
    modifier: Modifier = Modifier,
    onCall: () -> Unit, onZalo: () -> Unit, onSms: () -> Unit, onNavigate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        DetailActionButton("Gọi", Icons.Default.Call, Orange, onCall)
        DetailActionButton("Zalo", Icons.Default.Chat, Orange, onZalo)
        DetailActionButton("SMS", Icons.Default.ChatBubbleOutline, Orange, onSms)
        DetailActionButton("Đường", Icons.Default.Navigation, Orange, onNavigate)
        DetailActionButton("Sửa", Icons.Default.Edit, Orange, onEdit)
        DetailActionButton("Xóa", Icons.Default.DeleteOutline, Color(0xFFE21B1B), onDelete)
    }
}

@Composable
private fun DetailActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 45.dp, height = 42.dp).clickable { onClick() },
        shape = RoundedCornerShape(9.dp), colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(2.dp))
            Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CustomerDetailContent(
    customer: Customer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Card(
            Modifier.fillMaxWidth().height(306.dp), RoundedCornerShape(14.dp),
            CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (customer.photoUri.isNotBlank()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                        update = { imageView ->
                            val uri = android.net.Uri.parse(customer.photoUri)
                            val bitmap = runCatching {
                                when (uri.scheme) {
                                    "file" -> android.graphics.BitmapFactory.decodeFile(uri.path)
                                    else -> context.contentResolver.openInputStream(uri)?.use(android.graphics.BitmapFactory::decodeStream)
                                }
                            }.getOrNull()
                            if (bitmap != null) imageView.setImageBitmap(bitmap) else imageView.setImageDrawable(null)
                        }
                    )
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storefront, "Cổng nhà khách", tint = Orange, modifier = Modifier.size(122.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Hình cổng nhà khách", color = TextGray, fontSize = 13.sp)
                    }
                }

            }
        }
        (listOf(customer.name) + customer.aliases).filter { it.isNotBlank() }.forEach { displayName ->
            Box(Modifier.padding(start = 50.dp)) { DetailInfoCard(Icons.Default.Person, displayName) }
        }
        (listOf(customer.phone) + customer.extraPhones.map { it.number }).filter { it.isNotBlank() }.forEach { displayPhone ->
            Box(Modifier.padding(start = 50.dp)) { DetailInfoCard(Icons.Default.Phone, displayPhone) }
        }
        (listOf(CustomerAddress(customer.address, customer.latitude, customer.longitude, true)) + customer.extraAddresses).filter { it.address.isNotBlank() }.forEach { addr ->
            Card(
                Modifier.fillMaxWidth().padding(start = 50.dp), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(addr.address, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (addr.latitude.isBlank() || addr.longitude.isBlank()) "Chưa có tọa độ" else "${addr.latitude}, ${addr.longitude}", color = TextGray, fontSize = 11.sp)
                    }
                }
            }
        }
        if (customer.note.isNotBlank()) Card(
            Modifier.fillMaxWidth().padding(start = 50.dp), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) { Text(customer.note, Modifier.padding(7.dp), color = Navy, fontSize = 13.sp) }
    }
}

@Composable
private fun DetailInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(value, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ================================================================
// 9. THÊM / SỬA KHÁCH HÀNG
// ================================================================
private data class PhoneDraft(val number: String, val canCall: Boolean, val canZalo: Boolean, val canSms: Boolean)
private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean)

@Composable
fun CustomerFormScreen(customer: Customer?, onBack: () -> Unit, onSave: (Customer) -> Unit) {
    val context = LocalContext.current
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var note by remember(customer?.id) { mutableStateOf(customer?.note.orEmpty()) }
    var photoUri by remember(customer?.id) { mutableStateOf(customer?.photoUri.orEmpty()) }
    val names = remember(customer?.id) { mutableStateListOf<String>().apply { add(customer?.name.orEmpty()); addAll(customer?.aliases.orEmpty()) } }
    var expandedPhone by remember { mutableStateOf<Int?>(null) }
    var pickAddressIndex by remember { mutableStateOf<Int?>(null) }
    var validation by remember { mutableStateOf(false) }
    var showFormPhotoMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        photoUri = uri.toString()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCameraUri?.let { photoUri = it.toString() }
        pendingCameraUri = null
    }

    val phones = remember(customer?.id) {
        mutableStateListOf<PhoneDraft>().apply {
            if (customer == null) add(PhoneDraft("", true, true, true)) else {
                add(PhoneDraft(customer.phone, customer.primaryCanCall, customer.primaryCanZalo, customer.primaryCanSms))
                addAll(customer.extraPhones.map { PhoneDraft(it.number, it.canCall, it.canZalo, it.canSms) })
            }
        }
    }
    val addresses = remember(customer?.id) {
        mutableStateListOf<AddressDraft>().apply {
            if (customer == null) add(AddressDraft("", "", "", true)) else {
                add(AddressDraft(customer.address, customer.latitude, customer.longitude, true))
                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false) })
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader(if (customer == null) "THÊM KHÁCH HÀNG" else "SỬA KHÁCH HÀNG", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                CustomerPhotoCard(photoUri)
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp).size(38.dp).clickable { showFormPhotoMenu = true }, CircleShape, color = Orange) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, "Quản lý ảnh", tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
            }
            names.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { CompactInput(if (index == 0) "Tên khách hàng" else "Tên / biệt danh", value, { v -> names[index] = v; if (index == 0) name = v }, if (index == 0) "Nhập tên khách hàng" else "Nhập tên hoặc biệt danh") }
                    SmallPlusMinus(plus = true) { names.add(index + 1, "") }
                    if (names.size > 1 && index > 0) SmallPlusMinus(plus = false) { names.removeAt(index) }
                }
            }

            Text("SỐ ĐIỆN THOẠI", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            phones.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = item.number, onValueChange = { phones[index] = item.copy(number = it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(if (index == 0) "SĐT chính" else "SĐT phụ", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            IconButton(onClick = { expandedPhone = if (expandedPhone == index) null else index }, modifier = Modifier.size(30.dp)) { Icon(if (expandedPhone == index) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Chọn chức năng") }
                            SmallPlusMinus(plus = true) { phones.add(index + 1, PhoneDraft("", false, false, false)) }
                            if (phones.size > 1) SmallPlusMinus(plus = false) { phones.removeAt(index); expandedPhone = null }
                        }
                        if (expandedPhone == index) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ActionCheck("Gọi", item.canCall) { phones[index] = phones[index].copy(canCall = it) }
                            ActionCheck("Zalo", item.canZalo) { phones[index] = phones[index].copy(canZalo = it) }
                            ActionCheck("SMS", item.canSms) { phones[index] = phones[index].copy(canSms = it) }
                        }
                    }
                }
            }

            Text("ĐỊA CHỈ + ĐỊNH VỊ", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            addresses.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.isPrimary, onCheckedChange = { checked -> if (checked) addresses.indices.forEach { i -> addresses[i] = addresses[i].copy(isPrimary = i == index) } }, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(3.dp))
                            OutlinedTextField(value = item.address, onValueChange = { addresses[index] = item.copy(address = it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Địa chỉ", fontSize = 12.sp) })
                            SmallPlusMinus(plus = true) { addresses.add(index + 1, AddressDraft("", "", "", false)) }
                            if (addresses.size > 1) SmallPlusMinus(plus = false) {
                                val wasPrimary = addresses[index].isPrimary; addresses.removeAt(index)
                                if (wasPrimary && addresses.isNotEmpty()) addresses[0] = addresses[0].copy(isPrimary = true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fun setCoord(raw: String, latitudeField: Boolean) {
                                val pair = Regex("""([-+]?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)""").find(raw.trim())
                                if (pair != null) {
                                    var a = pair.groupValues[1]; var b = pair.groupValues[2]
                                    val av=a.toDoubleOrNull(); val bv=b.toDoubleOrNull()
                                    if (av != null && bv != null && kotlin.math.abs(av) > 90 && kotlin.math.abs(bv) <= 90) { val t=a; a=b; b=t }
                                    addresses[index] = addresses[index].copy(latitude = a, longitude = b)
                                } else if (latitudeField) addresses[index] = addresses[index].copy(latitude = raw.replace(',', '.'))
                                else addresses[index] = addresses[index].copy(longitude = raw.replace(',', '.'))
                            }
                            OutlinedTextField(item.latitude, { setCoord(it, true) }, Modifier.weight(1f), singleLine = true, label = { Text("Vĩ độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(item.longitude, { setCoord(it, false) }, Modifier.weight(1f), singleLine = true, label = { Text("Kinh độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            TextButton(onClick = { pickAddressIndex = index }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(2.dp)); Text("Chọn trên bản đồ", fontSize = 11.sp) }
                        }
                    }
                }
            }

            CompactInput("Ghi chú", note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
            if (validation) Text("Cần nhập tên, ít nhất 1 SĐT và 1 địa chỉ.", color = Color(0xFFE21B1B), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                val validPhones = phones.filter { it.number.isNotBlank() }; val validAddresses = addresses.filter { it.address.isNotBlank() }
                if (name.isBlank() || validPhones.isEmpty() || validAddresses.isEmpty()) validation = true else {
                    val primaryAddress = validAddresses.firstOrNull { it.isPrimary } ?: validAddresses.first(); val primaryPhone = validPhones.first()
                    onSave(Customer(id = customer?.id ?: 0L, name = name.trim(), phone = primaryPhone.number.trim(), address = primaryAddress.address.trim(), latitude = primaryAddress.latitude.trim(), longitude = primaryAddress.longitude.trim(), initials = createInitials(name), aliases = names.drop(1).map { it.trim() }.filter { it.isNotBlank() }, extraPhones = validPhones.drop(1).map { CustomerPhone(it.number.trim(), if (it.canCall) "Gọi" else if (it.canZalo) "Zalo" else "SMS", it.canCall, it.canZalo, it.canSms) }, extraAddresses = validAddresses.filter { it !== primaryAddress }.map { CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false) }, note = note.trim(), primaryCanCall = primaryPhone.canCall, primaryCanZalo = primaryPhone.canZalo, primaryCanSms = primaryPhone.canSms, photoUri = photoUri))
                }
            }, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("LƯU", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("Hủy") }
        }
    }

    if (showFormPhotoMenu) AlertDialog(
        onDismissRequest = { showFormPhotoMenu = false },
        title = { Text("Ảnh cổng nhà khách") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { showFormPhotoMenu = false; galleryLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Chọn từ thư viện") }
            FilledTonalButton(onClick = {
                showFormPhotoMenu = false
                val file = java.io.File(context.filesDir, "customer_gate_${customer?.id ?: 0L}_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                pendingCameraUri = uri; cameraLauncher.launch(uri)
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Chụp ảnh mới") }
            FilledTonalButton(onClick = { photoUri = ""; showFormPhotoMenu = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE21B1B)); Spacer(Modifier.width(6.dp)); Text("Xóa ảnh", color = Color(0xFFE21B1B)) }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { showFormPhotoMenu = false }) { Text("Hủy") } }
    )

    pickAddressIndex?.let { index ->
        CustomerCoordinateMapPicker(initialPoint = pointFromStrings(addresses[index].latitude, addresses[index].longitude), focusUserLocation = true, onDismiss = { pickAddressIndex = null }, onSavePoint = { point ->
            addresses[index] = addresses[index].copy(latitude = "%.6f".format(java.util.Locale.US, point.latitude), longitude = "%.6f".format(java.util.Locale.US, point.longitude)); pickAddressIndex = null
        })
    }
}

@Composable
private fun ActionCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChecked(!checked) }) {
        Checkbox(checked, onChecked, modifier = Modifier.size(32.dp)); Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun SmallPlusMinus(plus: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(if (plus) Icons.Default.AddCircle else Icons.Default.RemoveCircle, if (plus) "Thêm" else "Xóa", tint = if (plus) Orange else Color(0xFFE21B1B), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CompactInput(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true, minLines: Int = 1) {
    Column {
        Text(label, color = TextGray, fontSize = 11.sp)
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), placeholder = { Text(placeholder, fontSize = 12.sp) }, singleLine = singleLine, minLines = minLines, shape = RoundedCornerShape(12.dp))
    }
}

@Composable
private fun CustomerPhotoCard(photoUri: String = "") {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (photoUri.isNotBlank()) {
                AndroidView(
                    factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                    update = { image -> runCatching { image.setImageURI(android.net.Uri.parse(photoUri)) }.onFailure { image.setImageDrawable(null) } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, "Ảnh khách hàng", tint = Orange, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(4.dp)); Text("Hình cổng nhà khách", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CustomerCoordinateMapPicker(
    initialPoint: MapPoint?,
    focusUserLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val driverLocation by rememberDriverLocation()
    var selected by remember { mutableStateOf(initialPoint ?: driverLocation ?: DEFAULT_MAP_POINT) }
    val start = if (focusUserLocation) (driverLocation ?: initialPoint ?: DEFAULT_MAP_POINT) else (initialPoint ?: DEFAULT_MAP_POINT)

    // GPS thường trả về sau khi dialog đã mở. Khi có vị trí thật, chọn và focus ngay vào người dùng.
    LaunchedEffect(driverLocation, focusUserLocation) {
        if (focusUserLocation) driverLocation?.let { selected = it }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 72.dp),
            shape = RoundedCornerShape(14.dp), color = Background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Đóng") }
                    Text("CHỌN TỌA ĐỘ", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    driverLocation?.let { TextButton(onClick = { selected = it }) { Text("Vị trí tôi", fontSize = 11.sp) } }
                }
                CoordinatePickerMap(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    initialPoint = start,
                    driverLocation = driverLocation,
                    selectedPoint = selected,
                    onPointSelected = { selected = it }
                )
                Row(
                    Modifier.fillMaxWidth().height(48.dp).background(Color.White).padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.35f).fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("HỦY", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { onSavePoint(selected) },
                        modifier = Modifier.weight(0.65f).fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("LƯU TỌA ĐỘ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}


// ================================================================
// THANH 3 TAB DƯỚI CÙNG
// ================================================================
@Composable
fun BottomTabs(selected: Tab, onSelected: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).background(Orange)) {
        BottomTabItem(selected == Tab.MAP, "Bản đồ", Icons.Default.Map) { onSelected(Tab.MAP) }
        BottomTabItem(selected == Tab.ORDERS, "Chi tiết đơn", Icons.Default.ReceiptLong) { onSelected(Tab.ORDERS) }
        BottomTabItem(selected == Tab.CUSTOMERS, "Khách hàng", Icons.Default.People) { onSelected(Tab.CUSTOMERS) }
    }
}

@Composable
fun RowScope.BottomTabItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFFF3F0EC) else Orange
    val fg = if (selected) OrangeDark else Color.White
    Column(
        Modifier.weight(1f).fillMaxHeight().background(bg).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(19.dp))
        Text(label, color = fg, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun createInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
private fun CoordinatePickerMap(
    modifier: Modifier,
    initialPoint: MapPoint,
    driverLocation: MapPoint?,
    selectedPoint: MapPoint,
    onPointSelected: (MapPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var readyMap by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromJson(goongStyle(context))) {
                        readyMap = map
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(initialPoint.latitude, initialPoint.longitude))
                            .zoom(16.0)
                            .build()
                        map.addOnMapClickListener { latLng ->
                            onPointSelected(MapPoint(latLng.latitude, latLng.longitude))
                            true
                        }
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { map ->
                if (map.style != null) readyMap = map
            }
        }
    )

    // Mỗi khi GPS người dùng xuất hiện/cập nhật, camera focus ngay vào vị trí đó.
    LaunchedEffect(readyMap, driverLocation) {
        val map = readyMap ?: return@LaunchedEffect
        driverLocation?.let { point ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0)
            )
        }
    }

    LaunchedEffect(readyMap, selectedPoint, driverLocation) {
        val map = readyMap ?: return@LaunchedEffect
        map.clear()
        val selectedBitmap = createNumberBubbleDrawable(context, 1, false)
        val selectedIcon = IconFactory.getInstance(context).fromBitmap(
            (selectedBitmap as android.graphics.drawable.BitmapDrawable).bitmap
        )
        map.addMarker(
            MarkerOptions()
                .position(LatLng(selectedPoint.latitude, selectedPoint.longitude))
                .icon(selectedIcon)
                .title("Vị trí đã chọn")
        )
        driverLocation?.let { point ->
            val driverIcon = IconFactory.getInstance(context).fromBitmap(createDriverMotorbikeBitmap(context))
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .icon(driverIcon)
                    .title("Vị trí của tôi")
            )
        }
    }
}

// ================================================================
// 10. Ô TÌM KIẾM + SỐ THỨ TỰ
// ================================================================
@Composable
fun SearchBox(value:String,onValueChange:(String)->Unit,onClear:()->Unit,onQrClick:()->Unit){
    OutlinedTextField(
        value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
        placeholder={Text("Tìm mã đơn, tên người nhận...",color=TextGray,fontSize=14.sp,maxLines=1)},
        leadingIcon={Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Orange).clickable{onQrClick()},contentAlignment=Alignment.Center){Icon(Icons.Default.QrCodeScanner,"Quét QR",tint=Color.White,modifier=Modifier.size(23.dp))}},
        trailingIcon={if(value.isNotBlank()) IconButton(onClick=onClear){Icon(Icons.Default.Close,"Xóa",tint=TextGray,modifier=Modifier.size(20.dp))}},
        shape=RoundedCornerShape(14.dp),singleLine=true,
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Orange,unfocusedBorderColor=Border,focusedContainerColor=Color(0xFFF7F5F2),unfocusedContainerColor=Color(0xFFF7F5F2),cursorColor=OrangeDark))
}

@Composable
fun NumberCircle(number: Int, selected: Boolean) {
    Box(
        Modifier
            .size(28.dp) // Bong bóng STT lớn hơn một nấc.
            .clip(CircleShape)
            .background(if (selected) Orange else Color(0xFF4E5B6B)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = Color.White,
            fontSize = 13.sp, // Số STT lớn hơn một nấc.
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ================================================================
// 11. VIEWMODEL
// ================================================================
class MainViewModel : androidx.lifecycle.ViewModel() {
    // Đơn hàng hiện vẫn dùng dữ liệu mẫu; sau này có thể thay bằng Repository/API.
    val orders = sampleOrders

    // mutableStateListOf giúp Compose tự vẽ lại danh sách khi thêm, sửa hoặc xóa khách.
    private val customerState = mutableStateListOf<Customer>().apply { addAll(sampleCustomers) }

    // UI chỉ đọc danh sách thông qua property này để tránh sửa trực tiếp từ màn hình.
    val customers: List<Customer> get() = customerState

    // Tìm một khách theo id để màn hình chi tiết luôn lấy dữ liệu mới nhất.
    fun findCustomer(id: Long): Customer? = customerState.firstOrNull { it.id == id }

    // Thêm khách mới và trả về id vừa tạo để màn hình có thể mở chi tiết ngay.
    fun addCustomer(customer: Customer): Long {
        val newId = (customerState.maxOfOrNull { it.id } ?: 0L) + 1L // Sinh id tăng dần trong bản demo.
        customerState.add(customer.copy(id = newId)) // Thêm bản sao có id mới vào state list.
        return newId // Trả id cho lớp điều hướng.
    }

    // Cập nhật toàn bộ thông tin của một khách theo id.
    fun updateCustomer(updatedCustomer: Customer) {
        val index = customerState.indexOfFirst { it.id == updatedCustomer.id } // Tìm vị trí khách cần sửa.
        if (index >= 0) customerState[index] = updatedCustomer // Gán phần tử mới để Compose tự cập nhật UI.
    }

    // Xóa khách theo id.
    fun deleteCustomer(id: Long) {
        customerState.removeAll { it.id == id } // Loại bỏ toàn bộ phần tử trùng id.
    }
}
