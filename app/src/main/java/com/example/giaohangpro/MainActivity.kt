package com.example.giaohangpro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.maplibre.android.maps.MapView

// ================================================================
// 1. MÀU CHỦ ĐẠO
// ================================================================
// Muốn đổi toàn bộ giao diện sang màu khác, chỉnh các hằng số ở đây.
// Màu cam được lấy theo tinh thần hình mẫu người dùng gửi.
internal val Orange = Color(0xFFC65A22)
internal val OrangeDark = Color(0xFFA94318)
internal val OrangeLight = Color(0xFFFFF3EC)
internal val Blue = Orange
internal val Navy = Color(0xFF3E3A37)
internal val TextGray = Color(0xFF77716C)
internal val Background = Color(0xFFF6F4F1)
internal val Border = Color(0xFFDDD8D2)
private val CardWhite = Color.White
internal val MoneyGreen = Color(0xFF168A45)

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
    val shop: String = "",
    val status: String = "TT500", // Chỉ dùng TT500, TT506, TT507, TT508, TT505 hoặc TT515.
    val latitude: String = "", // Vĩ độ điểm giao; marker chỉ hiện khi tọa độ hợp lệ.
    val longitude: String = "", // Kinh độ điểm giao; dùng cùng với latitude để dẫn đường.
    val locallyDelivered: Boolean = false // Dấu cục bộ, được xóa khi nhập lại MVĐ; không phải trạng thái VTMan.
)

// Mỗi khách hàng có một id ổn định để khi sửa/xóa không bị nhầm khách có cùng tên.
private val AllowedOrderStatuses = setOf("TT500", "TT506", "TT507", "TT508", "TT505", "TT515")
private val TerminalOrderStatuses = setOf("TT505", "TT515")
private val HighlightServiceCodes = setOf("GGDH", "GG1P", "PTTX", "GBP")

private fun normalizeOrderStatus(raw: String): String {
    val clean = raw.trim().uppercase()
    return when {
        clean in AllowedOrderStatuses -> clean
        else -> "TT500"
    }
}

internal fun isTerminalOrderStatus(status: String): Boolean =
    normalizeOrderStatus(status) in TerminalOrderStatuses

private fun orderStatusColor(status: String): Color = when (normalizeOrderStatus(status)) {
    "TT500" -> Color(0xFF1976D2)
    "TT506", "TT507", "TT508" -> Color(0xFFF57C00)
    "TT505", "TT515" -> Color(0xFFD32F2F)
    else -> Color(0xFF1976D2)
}

data class Customer(
    val id: Long, // Mã định danh nội bộ của khách hàng.
    val name: String, // Tên chính hiển thị trong danh sách và màn hình chi tiết.
    val phone: String, // Số điện thoại chính để gọi nhanh.
    val address: String, // Địa chỉ giao hàng chính.
    val streetName: String = "", // Tên đường dùng để gom các tọa độ và học thói quen tạo tuyến.
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
    val isPrimary: Boolean = false,
    val photoUri: String = ""
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
enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER, VTMAN_EXPORT, AUTO_EXPORT, CUSTOMER_BACKUP, STREET_NAME_MANAGEMENT }

@Composable
fun GiaoHangApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.MAP) }
    var screen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var formIsNew by remember { mutableStateOf(false) }
    var returnOrderCode by remember { mutableStateOf<String?>(null) }
    var returnOrderCustomerId by remember { mutableStateOf<Long?>(null) }
    var mapFocusOrderCode by remember { mutableStateOf<String?>(null) }
    val selectedCustomer = selectedCustomerId?.let(vm::findCustomer)
    val context = LocalContext.current
    var lastBackPressAt by remember { mutableLongStateOf(0L) }

    androidx.activity.compose.BackHandler(enabled = true) {
        when (screen) {
            AppScreen.CUSTOMER_DETAIL -> {
                screen = AppScreen.MAIN
                if (returnOrderCode != null) tab = Tab.ORDERS
            }
            AppScreen.CUSTOMER_FORM -> screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL
            AppScreen.SETTINGS -> screen = AppScreen.MAIN
            AppScreen.MONEY_LEDGER -> screen = AppScreen.SETTINGS
            AppScreen.VTMAN_EXPORT -> screen = AppScreen.SETTINGS
            AppScreen.AUTO_EXPORT -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.STREET_NAME_MANAGEMENT -> screen = AppScreen.SETTINGS
            AppScreen.MAIN -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastBackPressAt <= 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressAt = now
                    Toast.makeText(context, "Bấm Back lần nữa để thoát", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    when (screen) {
        AppScreen.MAIN -> Scaffold(
            topBar = { TopHeader(onSettingsClick = { screen = AppScreen.SETTINGS }) },
            bottomBar = { BottomTabs(selected = tab, onSelected = { tab = it }) }
        ) { padding ->
            // MapScreen luôn được giữ trong composition để MapView không bị tạo lại khi đổi tab.
            // Các tab khác chỉ phủ một lớp giao diện lên trên; vì vậy đổi tab không còn reload bản đồ.
            Box(Modifier.fillMaxSize().padding(padding).background(Background)) {
                MapScreen(
                    vm = vm,
                    active = tab == Tab.MAP,
                    focusOrderCode = mapFocusOrderCode,
                    onFocusConsumed = { mapFocusOrderCode = null },
                    onOpenOrder = { code ->
                        // MapScreen stays composed behind other tabs to keep MapView warm.
                        // Ignore any stray/background map-list click unless the Map tab is actually active.
                        if (tab == Tab.MAP) {
                            returnOrderCode = code
                            returnOrderCustomerId = buildDeliveryGroups(vm.orders, vm.customers)
                                .firstOrNull { group -> group.orders.any { it.code == code } }
                                ?.customer?.id
                            tab = Tab.ORDERS
                        }
                    }
                )

                when (tab) {
                    Tab.MAP -> Unit
                    Tab.ORDERS -> Box(Modifier.fillMaxSize().background(Background)) {
                        OrderListScreen(
                            vm = vm,
                            focusOrderCode = returnOrderCode,
                            focusCustomerId = returnOrderCustomerId,
                            onFocusConsumed = {
                                returnOrderCode = null
                                returnOrderCustomerId = null
                            },
                            onNumberClick = { order -> mapFocusOrderCode = order.code; tab = Tab.MAP },
                            onDeliveryFocusNext = { order -> mapFocusOrderCode = order.code },
                            onCustomerClick = { order ->
                                returnOrderCode = order.code
                                val customer = vm.findCustomerByPhone(order.phone)
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
            }
        }
        AppScreen.CUSTOMER_DETAIL -> {
            val c = selectedCustomer
            if (c == null) screen = AppScreen.MAIN else CustomerDetailScreen(
                customer = c,
                onBack = {
                    screen = AppScreen.MAIN
                    if (returnOrderCode != null) tab = Tab.ORDERS
                },
                onEdit = { formIsNew = false; screen = AppScreen.CUSTOMER_FORM },
                onPhotoChanged = { uri -> vm.updateCustomerPhoto(c.id, uri) },
                onDelete = { vm.deleteCustomer(c.id); selectedCustomerId = null; screen = AppScreen.MAIN }
            )
        }
        AppScreen.CUSTOMER_FORM -> CustomerFormScreen(
            customer = if (formIsNew) null else selectedCustomer,
            streetNameOptions = vm.streetNames,
            onStreetNameCreated = vm::addStreetName,
            onBack = { screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL },
            onSave = { edited, original ->
                if (formIsNew) {
                    selectedCustomerId = vm.addCustomer(edited)
                    screen = AppScreen.CUSTOMER_DETAIL
                } else if (original != null && vm.updateCustomer(edited, original)) {
                    selectedCustomerId = edited.id
                    screen = AppScreen.CUSTOMER_DETAIL
                } else {
                    android.widget.Toast.makeText(context, "Khách đã thay đổi trong lúc sửa. Hãy mở lại để tránh ghi đè dữ liệu mới.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )
        AppScreen.SETTINGS -> SettingsScreen(
            onBack = { screen = AppScreen.MAIN },
            onMoneyLedger = { screen = AppScreen.MONEY_LEDGER },
            onVtmanExport = { screen = AppScreen.VTMAN_EXPORT },
            onAutoExport = { screen = AppScreen.AUTO_EXPORT },
            onCustomerBackup = { screen = AppScreen.CUSTOMER_BACKUP },
            onStreetNameManagement = { screen = AppScreen.STREET_NAME_MANAGEMENT }
        )
        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.AUTO_EXPORT -> AutoExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.STREET_NAME_MANAGEMENT -> StreetNameManagementScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
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
private fun SettingsScreen(
    onBack: () -> Unit,
    onMoneyLedger: () -> Unit,
    onVtmanExport: () -> Unit,
    onAutoExport: () -> Unit,
    onCustomerBackup: () -> Unit,
    onStreetNameManagement: () -> Unit
) {
    val context = LocalContext.current
    val routePrefs = remember { context.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE) }
    var showRouteSettings by remember { mutableStateOf(false) }
    var showAnchorPicker by remember { mutableStateOf(false) }
    var anchorLat by remember { mutableStateOf(routePrefs.getString("route_anchor_lat_v1", "").orEmpty()) }
    var anchorLng by remember { mutableStateOf(routePrefs.getString("route_anchor_lng_v1", "").orEmpty()) }
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
                SettingsItem(Icons.Default.Sync, "ĐỒNG BỘ DỮ LIỆU KHÁCH HÀNG", "Backup cục bộ theo phiên, đặt lịch và khôi phục dữ liệu") { onCustomerBackup() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ và lấy thông tin đơn trực tiếp từ VTMan") { onVtmanExport() }
                SettingsDivider()
                SettingsItem(Icons.Default.AutoAwesome, "AUTO EXPORT", "Chỉ nhập số lượng, app tự gom MVĐ và lấy dữ liệu") { onAutoExport() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { showRouteSettings = true }
                SettingsDivider()
                SettingsItem(
                    Icons.Default.Map,
                    "QUẢN LÝ TÊN ĐƯỜNG",
                    "Xem, sửa hoặc xóa các tên đường đã lưu"
                ) { onStreetNameManagement() }
            }
            SettingsSection("TÀI CHÍNH") {
                SettingsItem(Icons.Default.Payments, "BẢNG KÊ TIỀN", onClick = onMoneyLedger)
                SettingsDivider()
                SettingsItem(Icons.Default.AccountBalance, "CHECK CHUYỂN KHOẢN") { Toast.makeText(context,"Check chuyển khoản",Toast.LENGTH_SHORT).show() }
            }
        }
    }


    if (showRouteSettings) {
        AlertDialog(
            onDismissRequest = { showRouteSettings = false },
            title = { Text("Tuyến giao hàng") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (anchorLat.isBlank() || anchorLng.isBlank()) "Chưa có Điểm Neo" else "Điểm Neo: $anchorLat, $anchorLng", color = TextGray, fontSize = 12.sp)
                    Button(onClick = { showRouteSettings = false; showAnchorPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Place, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ĐIỂM NEO")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRouteSettings = false }) { Text("ĐÓNG") } }
        )
    }

    if (showAnchorPicker) {
        CustomerCoordinateMapPicker(
            initialPoint = pointFromStrings(anchorLat, anchorLng),
            focusUserLocation = true,
            onDismiss = { showAnchorPicker = false },
            onSavePoint = { point ->
                anchorLat = "%.6f".format(java.util.Locale.US, point.latitude)
                anchorLng = "%.6f".format(java.util.Locale.US, point.longitude)
                routePrefs.edit().putString("route_anchor_lat_v1", anchorLat).putString("route_anchor_lng_v1", anchorLng).apply()
                showAnchorPicker = false
                Toast.makeText(context, "Đã lưu Điểm Neo", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun StreetNameManagementScreen(vm: MainViewModel, onBack: () -> Unit) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingEdit by remember { mutableStateOf<String?>(null) }
    var editedName by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    val streetNames = vm.streetNames
        .sortedBy { it.lowercase(java.util.Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("QUẢN LÝ TÊN ĐƯỜNG", onBack)
        if (streetNames.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Map, null, tint = TextGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Chưa có tên đường nào", color = TextGray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(streetNames, key = { it.lowercase(java.util.Locale.getDefault()) }) { streetName ->
                    val customerCount = vm.customers.count {
                        it.streetName.trim().equals(streetName, ignoreCase = true)
                    }
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(OrangeLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Map, null, tint = Orange, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(streetName, color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "$customerCount khách hàng đang chọn",
                                    color = TextGray,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { pendingEdit = streetName; editedName = streetName; editError = null },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Edit, "Sửa tên đường", tint = Orange, modifier = Modifier.size(22.dp))
                            }
                            IconButton(
                                onClick = { pendingDelete = streetName },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    "Xóa tên đường",
                                    tint = Color(0xFFE21B1B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingEdit?.let { streetName ->
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text("Sửa tên đường") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it; editError = null },
                        label = { Text("Tên đường") },
                        singleLine = true,
                        isError = editError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    editError?.let { Text(it, color = Color(0xFFE21B1B)) }
                    Text("Tên mới sẽ được cập nhật cho tất cả khách hàng đang chọn đường này và danh sách chọn khi thêm/sửa khách.", fontSize = 12.sp, color = TextGray)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editedName.isNotBlank(),
                    onClick = {
                        editError = vm.renameStreetName(streetName, editedName)
                        if (editError == null) pendingEdit = null
                    }
                ) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { pendingEdit = null }) { Text("HỦY") } }
        )
    }

    pendingDelete?.let { streetName ->
        val customerCount = vm.customers.count {
            it.streetName.trim().equals(streetName, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xóa tên đường") },
            text = {
                Text(
                    "Xóa \"$streetName\"? $customerCount khách hàng đang chọn tên đường này sẽ trở về trạng thái chưa chọn. Địa chỉ và tọa độ vẫn được giữ nguyên."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStreetName(streetName)
                    pendingDelete = null
                }) {
                    Text("XÓA", color = Color(0xFFE21B1B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("HỦY") }
            }
        )
    }
}


private fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)

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
                            OutlinedTextField(
                                value = if (counts[i] == 0) "" else counts[i].toString(),
                                onValueChange = { raw: String -> counts[i] = raw.filter(Char::isDigit).trimStart('0').toIntOrNull()?.coerceAtMost(9999) ?: 0 },
                                modifier = Modifier.width(62.dp).height(54.dp),
                                singleLine = true,
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )
                            IconButton(onClick = { counts[i] = (counts[i] + 1).coerceAtMost(9999) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, "Thêm", tint = Orange, modifier = Modifier.size(21.dp)) }
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

private object MoneyCommaVisualTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val formatted = raw.reversed().chunked(3).joinToString(",").reversed()
        val mapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                if (raw.isEmpty()) return 0
                val commasBefore = if (o == 0) 0 else ((raw.length - 1) / 3 - (raw.length - o - 1).coerceAtLeast(0) / 3).coerceAtLeast(0)
                return (o + commasBefore).coerceAtMost(formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceIn(0, formatted.length)
                return formatted.take(t).count { it != ',' }.coerceAtMost(raw.length)
            }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), mapping)
    }
}

@Composable
private fun MoneyEntryRow(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(label) { mutableStateOf(if (value == 0L) "" else value.toString()) }
    var lastEmitted by remember(label) { mutableLongStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            text = if (value == 0L) "" else value.toString()
            lastEmitted = value
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { raw: String ->
                val digits = raw.filter(Char::isDigit).trimStart('0')
                val parsed = digits.toLongOrNull() ?: 0L
                text = digits
                lastEmitted = parsed
                onValueChange(parsed)
            },
            modifier = Modifier.weight(1f).height(56.dp),
            singleLine = true,
            placeholder = { Text("0", fontSize = 14.sp) },
            suffix = { Text("đ", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = MoneyCommaVisualTransformation,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        )
    }
}


@Composable
private fun VtmanExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var waybills by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(com.example.giaohangpro.vtman.VtmanQueueController.snapshot()) }
    var importedCount by remember { mutableIntStateOf(0) }

    fun currentCodes(): List<String> = waybills.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.uppercase() }
        .toList()

    fun loadQueue(codes: List<String>) {
        waybills = codes.joinToString("\n")
        com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
        importedCount = 0
        snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
    }

    val continuousScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanned = result.data
                ?.getStringArrayListExtra(ContinuousVtmanScanActivity.EXTRA_NEW_CODES)
                .orEmpty()
            if (scanned.isNotEmpty()) {
                val merged = (currentCodes() + scanned).distinctBy { it.uppercase() }
                loadQueue(merged)
                Toast.makeText(context, "Đã thêm ${scanned.size} MVĐ từ camera", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val imported = text.lineSequence()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter(String::isNotBlank)
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter(String::isNotBlank)
                .distinctBy { it.uppercase() }
                .toList()
            val existing = vm.orders.map { it.code.trim().uppercase() }.toSet()
            val newCodes = imported.filterNot { it.uppercase() in existing }
            loadQueue(newCodes)
            val skipped = imported.size - newCodes.size
            Toast.makeText(context, "Đã nạp ${newCodes.size} MVĐ" + if (skipped > 0) " · bỏ qua $skipped mã đã có" else "", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không đọc được file danh sách", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val records = com.example.giaohangpro.vtman.VtmanQueueController.records()
            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                importedCount = records.size
            }
            kotlinx.coroutines.delay(400)
        }
    }

    val codeCount = currentCodes().size
    val softOrange = Color(0xFFC95C22)
    val softOrangeDark = Color(0xFFB84F1B)

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(softOrange).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White)
                }
                Text("VTMan Export", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 6.dp, vertical = 6.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, ContinuousVtmanScanActivity::class.java).apply {
                                putStringArrayListExtra(ContinuousVtmanScanActivity.EXTRA_TEXTBOX_CODES, ArrayList(currentCodes()))
                                putStringArrayListExtra(ContinuousVtmanScanActivity.EXTRA_ORDER_CODES, ArrayList(vm.orders.map { it.code }))
                            }
                            continuousScanLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("QUÉT MVĐ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("QR / MÃ VẠCH", fontSize = 9.sp)
                        }
                    }

                    Button(
                        onClick = { csvImportLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("NẠP MVĐ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Từ file / danh sách", fontSize = 9.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
                            } else {
                                context.startService(android.content.Intent(context, com.example.giaohangpro.vtman.VtmanOverlayService::class.java))
                                Toast.makeText(context, "Mở VTMan > Gạch phát offline, rồi bấm Chạy trên popup", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("BẬT POPUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = softOrange),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) {
                        Icon(Icons.Default.AccessibilityNew, null, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("TRỢ NĂNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(277.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Column(Modifier.fillMaxSize().padding(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Danh sách MVĐ", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(12.dp), color = OrangeLight) {
                                Text("$codeCount mã", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = softOrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    waybills = ""
                                    com.example.giaohangpro.vtman.VtmanQueueController.load(emptyList())
                                    importedCount = 0
                                    snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                                    Toast.makeText(context, "Đã xóa toàn bộ MVĐ trong textbox", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD33B32))
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("XÓA", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = waybills,
                            onValueChange = { waybills = it },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Navy),
                            placeholder = { Text("Mỗi dòng 1 mã vận đơn", fontSize = 10.sp) },
                            singleLine = false,
                            maxLines = Int.MAX_VALUE,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Tiến độ: ${snapshot.processed}/${snapshot.total} • Lấy được: ${snapshot.written} • Bỏ qua: ${snapshot.skipped}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(snapshot.status, fontSize = 11.sp, color = if (snapshot.error.isBlank()) TextGray else Color(0xFFE21B1B))
                    if (snapshot.currentWaybill.isNotBlank()) {
                        Text("Đang xử lý: ${snapshot.currentWaybill}", fontSize = 11.sp, color = softOrangeDark)
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E6)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD29A))
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("💡 Hướng dẫn nhanh", color = softOrangeDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("• QUÉT MVĐ: camera quét liên tục QR / mã vạch.", color = Navy, fontSize = 10.sp)
                    Text("• Mã mới tự thêm; mã trùng sẽ hiện Toast.", color = Navy, fontSize = 10.sp)
                    Text("• XÓA: xóa toàn bộ mã trong textbox và hàng chờ.", color = Navy, fontSize = 10.sp)
                    Text("• Camera tự thoát sau 30 giây không quét được mã.", color = Navy, fontSize = 10.sp)
                }
            }
        }
    }
}

internal fun csvCell(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""


@Composable
private fun AutoExportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var countText by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(com.example.giaohangpro.vtman.VtmanQueueController.snapshot()) }
    var importedCount by remember { mutableIntStateOf(0) }
    val importWaybillCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Không đọc được file")
            }.onSuccess { csvText ->
                val count = com.example.giaohangpro.vtman.VtmanQueueController.importWaybillsFromCsv(csvText)
                snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                if (count > 0) {
                    importedCount = 0
                    Toast.makeText(context, "Đã nạp $count MVĐ từ file CSV", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, snapshot.error.ifBlank { "File CSV không có MVĐ hợp lệ" }, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(context, "Không đọc được file CSV: ${it.message.orEmpty()}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val records = com.example.giaohangpro.vtman.VtmanQueueController.records()
            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                importedCount = records.size
            }
            kotlinx.coroutines.delay(400)
        }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(Orange).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White)
                }
                Text("AUTO EXPORT", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Background)
                .verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Tách làm 2 bước: lấy MVĐ trước, sau đó mới Export dữ liệu đơn và SĐT. Sau bước 1 app tự lưu file CSV dự phòng vào Download; nếu bộ nhớ MVĐ bị mất, dùng nút Nạp MVĐ từ file CSV để tiếp tục bước 2 mà không phải quét lại.",
                color = Navy,
                fontSize = 13.sp
            )
            OutlinedTextField(
                value = countText,
                onValueChange = { countText = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Số lượng MVĐ cần lấy") },
                placeholder = { Text("Ví dụ: 25") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = {
                    val count = countText.toIntOrNull()
                    when {
                        count == null || count <= 0 -> Unit
                        !android.provider.Settings.canDrawOverlays(context) ->
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        else -> {
                            com.example.giaohangpro.vtman.VtmanQueueController.prepareAutoExport(count)
                            importedCount = 0
                            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                            context.startService(
                                android.content.Intent(context, com.example.giaohangpro.vtman.VtmanOverlayService::class.java)
                                    .putExtra(
                                        com.example.giaohangpro.vtman.VtmanOverlayService.EXTRA_AUTO_EXPORT_LOCKED,
                                        true
                                    )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(7.dp))
                Text("1. LẤY VÀ LƯU MVĐ", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    importWaybillCsvLauncher.launch(
                        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv")
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(7.dp))
                Text("NẠP MVĐ TỪ FILE CSV", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    when {
                        !com.example.giaohangpro.vtman.VtmanQueueController.hasLoadedWaybills() -> Unit
                        !android.provider.Settings.canDrawOverlays(context) ->
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        !com.example.giaohangpro.vtman.VtmanQueueController.requestDataExport() -> Unit
                        else -> {
                            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                            // Bước 2 chỉ chuẩn bị queue + popup. Người dùng tự mở VTMan
                            // và vào Gạch phát offline; không tự launch/chuyển app, không Toast.
                            context.startService(
                                android.content.Intent(
                                    context,
                                    com.example.giaohangpro.vtman.VtmanOverlayService::class.java
                                ).putExtra(
                                    com.example.giaohangpro.vtman.VtmanOverlayService.EXTRA_AUTO_EXPORT_LOCKED,
                                    true
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeDark),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(7.dp))
                Text("2. EXPORT DỮ LIỆU ĐƠN", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.AccessibilityNew, null)
                Spacer(Modifier.width(7.dp))
                Text("MỞ CÀI ĐẶT TRỢ NĂNG")
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TRẠNG THÁI", color = OrangeDark, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                com.example.giaohangpro.vtman.VtmanQueueController.clearAutoLog()
                                snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                            },
                            enabled = snapshot.runLog.isNotEmpty()
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("XÓA", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(snapshot.status, color = if (snapshot.error.isBlank()) Navy else Color(0xFFD32F2F), fontSize = 13.sp)
                    if (snapshot.total > 0) {
                        Text(
                            "Đã xử lý ${snapshot.processed}/${snapshot.total} · Lưu ${snapshot.written} · Bỏ qua ${snapshot.skipped}",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                    if (snapshot.runLog.isNotEmpty()) {
                        HorizontalDivider(color = Border)
                        Text("NHẬT KÝ LẦN CHẠY", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val logScroll = rememberScrollState()
                        LaunchedEffect(snapshot.runLog.size) {
                            kotlinx.coroutines.delay(80)
                            logScroll.scrollTo(logScroll.maxValue)
                        }
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(logScroll),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            snapshot.runLog.forEach { line ->
                                val lineColor = when {
                                    "✓" in line -> Color(0xFF168A45)
                                    "⚠" in line -> Color(0xFFF57C00)
                                    "✕" in line -> Color(0xFFD32F2F)
                                    else -> TextGray
                                }
                                Text(line, color = lineColor, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CustomerBackupResult(
    val total: Int,
    val added: Int,
    val updated: Int,
    val skipped: Int = 0,
    val images: Int = 0,
    val missingImages: Int = 0
)

@Composable
private fun CustomerBackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Sẵn sàng") }
    var refresh by remember { mutableIntStateOf(0) }
    var autoSync by remember(refresh) { mutableStateOf(CustomerLocalSync.isEnabled(context)) }
    var times by remember(refresh) { mutableStateOf(CustomerLocalSync.scheduledTimes(context)) }
    var pendingRestore by remember { mutableStateOf<CustomerSyncSession?>(null) }
    val sessions = remember(refresh) { CustomerLocalSync.sessions(context) }
    val dateFmt = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()) }
    var portableReady by remember(refresh) { mutableStateOf(CustomerLocalSync.portableUri(context) != null) }

    val createSyncFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang tạo file đồng bộ..."
            runCatching {
                CustomerLocalSync.setPortableUri(context, uri)
                withContext(Dispatchers.IO) {
                    CustomerLocalSync.syncNow(context)
                    CustomerLocalSync.writePortableBackup(context)
                }
            }.onSuccess {
                portableReady = true; refresh++
                status = "Đã liên kết file đồng bộ. Từ giờ lịch tự động sẽ cập nhật file này."
            }.onFailure { e -> status = "Không tạo được file đồng bộ: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }

    val openSyncFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang khôi phục từ file đồng bộ..."
            runCatching { withContext(Dispatchers.IO) { CustomerLocalSync.importPortableBackup(context, uri) } }
                .onSuccess { count ->
                    vm.reloadPersistentData(); portableReady = true; refresh++
                    status = "Đã khôi phục " + count + " khách và liên kết lại file đồng bộ."
                }
                .onFailure { e -> status = "Không đọc được file đồng bộ: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }

    fun doSync() {
        scope.launch {
            busy = true; status = "Đang đồng bộ thay đổi..."
            runCatching { withContext(Dispatchers.IO) { CustomerLocalSync.syncNow(context) } }
                .onSuccess { s ->
                    status = if (s == null) "Dữ liệu không thay đổi — không tạo phiên mới" else "Đồng bộ xong: " + s.upserts + " cập nhật, " + s.deletes + " xóa • " + s.customerCount + " khách"
                    refresh++
                }
                .onFailure { e -> status = "Đồng bộ lỗi: " + (e.message ?: "không xác định") + " • Phiên trước vẫn an toàn" }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang nhập ZIP dự phòng..."
            runCatching { vm.importCustomerBackup(context, uri) }
                .onSuccess { x -> status = "Nhập xong: +" + x.added + " khách mới, cập nhật " + x.updated }
                .onFailure { e -> status = "Không nhập được ZIP: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; status = "Đang xuất ZIP đầy đủ..."
            runCatching { vm.exportCustomerBackup(context, uri) }
                .onSuccess { x -> status = "Xuất ZIP xong " + x.total + " khách, " + x.images + " ảnh" }
                .onFailure { e -> status = "Không xuất được ZIP: " + (e.message ?: "lỗi không xác định") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("ĐỒNG BỘ KHÁCH HÀNG", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("BACKUP CỤC BỘ", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Chỉ lưu khách hàng thêm / sửa / xóa kể từ phiên trước. Dữ liệu nằm trên điện thoại, chưa gửi lên Internet.", color = TextGray, fontSize = 12.sp)
                    val last = sessions.firstOrNull()
                    Text(if (last == null) "Chưa có phiên đồng bộ" else "Phiên gần nhất: " + dateFmt.format(java.util.Date(last.createdAt)) + " • " + last.customerCount + " khách", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(enabled = !busy, onClick = { doSync() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("ĐỒNG BỘ NGAY", fontWeight = FontWeight.Bold)
            }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (portableReady) Color(0xFFE6F4EA) else OrangeLight)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (portableReady) "FILE ĐỒNG BỘ ĐÃ LIÊN KẾT" else "CHỌN NƠI LƯU FILE ĐỒNG BỘ", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(if (portableReady) "File nằm ngoài dữ liệu ứng dụng nên vẫn còn khi gỡ GiaoHangPro." else "Chọn một nơi lưu bên ngoài ứng dụng. Chỉ cần chọn một lần; các phiên sau app tự cập nhật file.", color = TextGray, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { createSyncFileLauncher.launch("GiaoHangPro_DongBoKhachHang.zip") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text(if (portableReady) "ĐỔI FILE" else "TẠO FILE", fontSize = 11.sp)
                        }
                        OutlinedButton(enabled = !busy, onClick = { openSyncFileLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Restore, null); Spacer(Modifier.width(4.dp)); Text("CHỌN FILE CŨ", fontSize = 11.sp)
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TỰ ĐỘNG THEO LỊCH", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (autoSync) "Lần kế tiếp: " + (CustomerLocalSync.nextSchedule(context) ?: "--") else "Đang tắt", color = TextGray, fontSize = 11.sp)
                        }
                        Switch(checked = autoSync, onCheckedChange = { autoSync = it; CustomerLocalSync.setEnabled(context, it); refresh++ })
                    }
                    times.forEach { time ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Orange, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                            Text(time, modifier = Modifier.weight(1f), color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { times = times - time; CustomerLocalSync.setScheduledTimes(context, times); refresh++ }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.DeleteOutline, "Xóa giờ", tint = TextGray) }
                        }
                    }
                    OutlinedButton(onClick = {
                        val now = java.util.Calendar.getInstance()
                        android.app.TimePickerDialog(context, { _, h, m ->
                            val t = String.format(java.util.Locale.US, "%02d:%02d", h, m)
                            times = (times + t).distinct().sorted(); CustomerLocalSync.setScheduledTimes(context, times); refresh++
                        }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.AddAlarm, null); Spacer(Modifier.width(6.dp)); Text("THÊM GIỜ ĐỒNG BỘ") }
                }
            }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = OrangeLight)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) { CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp); Spacer(Modifier.width(9.dp)) }
                    Text(status, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("LỊCH SỬ ĐỒNG BỘ", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (sessions.isEmpty()) Text("Chưa có dữ liệu. Bấm Đồng bộ ngay để tạo bản gốc đầu tiên.", color = TextGray, fontSize = 12.sp)
            sessions.forEachIndexed { index, s ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (index == 0) Icons.Default.Verified else Icons.Default.History, null, tint = if (index == 0) Color(0xFF2E7D32) else TextGray); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dateFmt.format(java.util.Date(s.createdAt)), color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(s.customerCount.toString() + " khách • +/sửa " + s.upserts + " • xóa " + s.deletes + if (index == 0) " • Bản an toàn" else "", color = TextGray, fontSize = 11.sp)
                        }
                        if (index != 0) TextButton(enabled = !busy, onClick = { pendingRestore = s }) { Text("KHÔI PHỤC", fontSize = 11.sp) }
                    }
                }
            }
            HorizontalDivider(color = Border)
            Text("IMPORT / EXPORT THỦ CÔNG", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Giữ ZIP đầy đủ để chuyển máy hoặc lưu ra nơi khác khi cần.", color = TextGray, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(4.dp)); Text("NHẬP ZIP", fontSize = 11.sp) }
                OutlinedButton(enabled = !busy, onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                    exportLauncher.launch("GiaoHangPro_KhachHang_" + stamp + ".zip")
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(4.dp)); Text("XUẤT ZIP", fontSize = 11.sp) }
            }
        }
    }
    pendingRestore?.let { s ->
        AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Khôi phục phiên này?") },
            text = { Text(dateFmt.format(java.util.Date(s.createdAt)) + " • " + s.customerCount + " khách\n\nDữ liệu hiện tại sẽ được đồng bộ bảo vệ trước khi khôi phục.") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                scope.launch {
                    busy = true; status = "Đang khôi phục..."
                    runCatching { withContext(Dispatchers.IO) { CustomerLocalSync.restore(context, s.id) } }
                        .onSuccess { count -> vm.reloadPersistentData(); status = "Đã khôi phục " + count + " khách"; refresh++ }
                        .onFailure { e -> status = "Khôi phục lỗi: " + (e.message ?: "không xác định") + " • Dữ liệu hiện tại không bị xóa" }
                    busy = false
                }
            }) { Text("KHÔI PHỤC") } }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("HỦY") } })
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


private fun uiNormPhone(raw: String): String = CustomerPhoneLookup.normalize(raw)

internal data class DeliveryGroup(val key: String, val customer: Customer?, val orders: List<Order>)

internal fun deliveryGroupPoint(group: DeliveryGroup): MapPoint? {
    val c = group.customer
    c?.let { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    group.orders.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    if (c == null) return null
    c.extraAddresses.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    return null
}

private fun deliveryGroupHasCoordinate(group: DeliveryGroup): Boolean = deliveryGroupPoint(group) != null

internal fun buildDeliveryGroups(orders: List<Order>, customers: List<Customer>): List<DeliveryGroup> {
    val customerByPhone = CustomerPhoneLookup(customers)
    val grouped = linkedMapOf<String, MutableList<Order>>()
    val customerForKey = mutableMapOf<String, Customer?>()
    orders.forEach { order ->
        val normalizedPhone = uiNormPhone(order.phone)
        val customer = customerByPhone.find(order.phone)
        val key = when {
            customer != null -> "C:${customer.id}"
            normalizedPhone.isNotBlank() -> "P:$normalizedPhone"
            else -> "O:${order.code}"
        }
        val displayKey = when {
            order.locallyDelivered -> "$key:LOCAL"
            isTerminalOrderStatus(order.status) -> "$key:TERMINAL"
            else -> key
        }
        grouped.getOrPut(displayKey) { mutableListOf() }.add(order)
        customerForKey[displayKey] = customer
    }
    return grouped.map { (key, list) -> DeliveryGroup(key, customerForKey[key], list) }
}

private fun orderMoneyValue(raw: String): Long = raw.filter(Char::isDigit).toLongOrNull() ?: 0L
private fun groupMoneyText(group: DeliveryGroup): String = fmtMoney(group.orders.sumOf { orderMoneyValue(it.amount) }) + "đ"
internal fun groupRepresentative(group: DeliveryGroup): Order {
    val first = group.orders.first()
    val c = group.customer
    val coordinates = CustomerCoordinateSafety.preferredCoordinates(c, first.latitude, first.longitude)
    return first.copy(
        customer = c?.name ?: first.customer,
        phone = c?.phone ?: first.phone,
        address = c?.address?.takeIf(String::isNotBlank) ?: first.address,
        latitude = coordinates.first,
        longitude = coordinates.second,
        amount = groupMoneyText(group),
        item = if (group.orders.size > 1) "${group.orders.size} MVĐ" else first.item
    )
}

// ================================================================
// 6. TAB CHI TIẾT ĐƠN
// ================================================================
@Composable
fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    focusCustomerId: Long? = null,
    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onDeliveryFocusNext: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val context = LocalContext.current
    val ordersSnapshot = vm.orders.toList()
    val customersSnapshot = vm.customers.toList()
    val allGroups = remember(ordersSnapshot, customersSnapshot) {
        buildDeliveryGroups(ordersSnapshot, customersSnapshot)
    }
    val activeGroups = remember(allGroups) {
        allGroups.filterNot { g -> g.orders.all { it.locallyDelivered || isTerminalOrderStatus(it.status) } }
    }
    val deliveredGroups = remember(allGroups) {
        allGroups.filter { g -> g.orders.all { it.locallyDelivered } }
    }
    val terminalGroups = remember(allGroups) {
        allGroups.filter { g -> g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) } }
    }
    val groupHasCoordinate = remember(allGroups) {
        allGroups.associate { it.key to deliveryGroupHasCoordinate(it) }
    }
    // Numbering base = active + locally delivered route stops, in original route order.
    // Terminal VTMan statuses never consume a route STT.
    val numberedRouteGroups = remember(allGroups) {
        allGroups.filterNot { g ->
            g.orders.all { !it.locallyDelivered && isTerminalOrderStatus(it.status) }
        }
    }
    var keyword by remember { mutableStateOf("") }
    var mapCustomerFilterId by remember { mutableStateOf<Long?>(null) }
    var showTools by remember { mutableStateOf(false) }
    var showAddOrder by remember { mutableStateOf(false) }
    var editPicker by remember { mutableStateOf(false) }
    var editOrder by remember { mutableStateOf<Order?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleteAllWasChosen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDeliveredGroup by remember { mutableStateOf<DeliveryGroup?>(null) }
    var deliveryFocusCode by remember { mutableStateOf<String?>(null) }
    val q = keyword.trim()

    LaunchedEffect(focusOrderCode, focusCustomerId) {
        val code = focusOrderCode ?: return@LaunchedEffect
        keyword = code
        mapCustomerFilterId = focusCustomerId
    }

    fun matches(g: DeliveryGroup): Boolean =
        mapCustomerFilterId?.let { wantedId -> g.customer?.id == wantedId } ?: (q.isBlank() ||
        g.orders.any { o ->
            o.code.contains(q, true) || o.customer.contains(q, true) ||
                o.phone.contains(q, true) || o.address.contains(q, true) ||
                o.shop.contains(q, true) || o.item.contains(q, true) || matchesCodSearch(o.amount, q)
        } || (g.customer?.name?.contains(q, true) == true))

    // Tab Chi tiết đơn: các điểm CHƯA có tọa độ thật luôn nằm đầu danh sách.
    // Chỉ đổi thứ tự hiển thị; activeGroups vẫn giữ thứ tự tuyến gốc để STT đã tạo không bị lệch.
    val matchedActiveGroups = remember(activeGroups, q, mapCustomerFilterId) { activeGroups.filter(::matches) }
    val pendingGroups = remember(matchedActiveGroups, groupHasCoordinate) {
        matchedActiveGroups.filterNot { groupHasCoordinate[it.key] == true }
    }
    val locatedGroups = remember(matchedActiveGroups, groupHasCoordinate) {
        matchedActiveGroups.filter { groupHasCoordinate[it.key] == true }
    }
    val visibleGroups = remember(pendingGroups, locatedGroups, deliveredGroups, terminalGroups, q, mapCustomerFilterId) {
        pendingGroups + locatedGroups + deliveredGroups.filter(::matches) + terminalGroups.filter(::matches)
    }
    val filteredOrders = remember(ordersSnapshot, q) {
        if (q.isBlank()) ordersSnapshot else ordersSnapshot.filter { o ->
            o.code.contains(q, true) || o.customer.contains(q, true) || o.phone.contains(q, true) ||
                o.address.contains(q, true) || o.shop.contains(q, true) || o.item.contains(q, true) || matchesCodSearch(o.amount, q)
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let {
            keyword = it
            mapCustomerFilterId = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val n = vm.importOrdersCsv(text)
            Toast.makeText(context, "Đã nhập $n đơn từ CSV", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Không đọc được CSV", Toast.LENGTH_LONG).show() }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("MVĐ,Shop,SĐT,Tên khách,COD,Địa chỉ,Hàng hóa,Trạng thái,Dịch vụ\n")
                vm.orders.forEach { o ->
                    append(listOf(o.code,o.shop,o.phone,o.customer,o.amount,o.address,o.item,o.status,o.tags.joinToString(" ")).joinToString(",") { csvCell(it) }).append('\n')
                }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context, "Đã xuất ${vm.orders.size} đơn", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context, "Không xuất được danh sách", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(focusOrderCode, visibleGroups) {
        val code = focusOrderCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.scrollToItem(i)
        onFocusConsumed()
    }

    LaunchedEffect(deliveryFocusCode, visibleGroups) {
        val code = deliveryFocusCode ?: return@LaunchedEffect
        val i = visibleGroups.indexOfFirst { g -> g.orders.any { it.code == code } }
        if (i >= 0) listState.animateScrollToItem(i)
        deliveryFocusCode = null
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Spacer(Modifier.height(6.dp))
            SearchBox(
                keyword,
                { value ->
                    keyword = value
                    mapCustomerFilterId = null
                },
                {
                    keyword = ""
                    mapCustomerFilterId = null
                }
            ) {
                scanLauncher.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")
                    setBeepEnabled(false)
                    setCaptureActivity(PortraitCaptureActivity::class.java)
                    setOrientationLocked(true)
                    setBarcodeImageEnabled(false)
                })
            }
            Spacer(Modifier.height(6.dp))

            if (deleteMode) {
                val allVisibleSelected = filteredOrders.isNotEmpty() && filteredOrders.all { it.code in selected }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allVisibleSelected,
                        onCheckedChange = { all ->
                            deleteAllWasChosen = all
                            selected = if (all) filteredOrders.map { it.code }.toSet() else emptySet()
                        }
                    )
                    Text("Chọn tất cả", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                    TextButton(onClick = {
                        deleteMode = false
                        selected = emptySet()
                        deleteAllWasChosen = false
                    }) { Text("HỦY") }
                    Button(
                        onClick = { if (selected.isNotEmpty()) confirmDelete = true },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("XÓA (${selected.size})")
                    }
                }
            } else {
                Text("${activeGroups.size} điểm giao • ${vm.orders.size} MVĐ", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))

            if (deleteMode) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(bottom = 62.dp)
                ) {
                    items(filteredOrders, key = { it.code }) { order ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = order.code in selected,
                                    onCheckedChange = { checked ->
                                        deleteAllWasChosen = false
                                        selected = if (checked) selected + order.code else selected - order.code
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(order.code, fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                                    Text("${order.customer} • ${order.phone}", color = TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(order.amount, color = MoneyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 62.dp)
                ) {
                    items(visibleGroups, key = { it.key }) { group ->
                        val delivered = group.orders.all { it.locallyDelivered }
                        val routeStt = if (delivered || !vm.routeNumberingEnabled || groupHasCoordinate[group.key] != true) {
                            null
                        } else {
                            vm.routeSttForGroup(group.orders.map(Order::code))
                                ?: numberedRouteGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)
                        }
                        DeliveryGroupCard(
                            routeStt = routeStt,
                            group = group,
                            delivered = delivered,
                            onNumberClick = { group.orders.firstOrNull()?.let(onNumberClick) },
                            onCustomerClick = { group.orders.firstOrNull()?.let(onCustomerClick) },
                            onDelivered = { pendingDeliveredGroup = group },
                            onRedeliver = { group.orders.firstOrNull()?.let { vm.redeliverGroup(it.code) } }
                        )
                    }
                }
            }
        }

        val orderListScope = rememberCoroutineScope()
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { orderListScope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.size(44.dp),
                containerColor = Color.White,
                contentColor = Orange
            ) { Icon(Icons.Default.KeyboardArrowUp, "Về đầu danh sách") }

            FloatingActionButton(
                onClick = { showTools = true },
                modifier = Modifier.size(44.dp),
                containerColor = Orange
            ) { Icon(Icons.Default.Edit, "Công cụ đơn", tint = Color.White) }
        }

        DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }, modifier = Modifier.align(Alignment.BottomStart)) {
            DropdownMenuItem(
                text = { Text("Thêm Đơn Hàng") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { showTools = false; showAddOrder = true }
            )
            DropdownMenuItem(
                text = { Text("Nhập danh sách đơn") },
                leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                onClick = { showTools = false; importLauncher.launch("text/*") }
            )
            DropdownMenuItem(
                text = { Text("Xuất danh sách đơn") },
                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                onClick = { showTools = false; exportLauncher.launch("giaohangpro_orders.csv") }
            )
            DropdownMenuItem(
                text = { Text("Sửa đơn hàng") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { showTools = false; editPicker = true }
            )
            DropdownMenuItem(
                text = { Text("Xóa đơn hàng") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = {
                    showTools = false
                    selected = emptySet()
                    deleteAllWasChosen = false
                    deleteMode = true
                }
            )
        }
    }

    pendingDeliveredGroup?.let { group ->
        val firstCode = group.orders.firstOrNull()?.code.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeliveredGroup = null },
            title = { Text("Xác nhận giao?") },
            text = {
                Text(
                    if (group.orders.size > 1)
                        "Xác nhận thao tác giao cho ${group.orders.size} MVĐ? Nạp lại từng MVĐ sẽ xóa dấu này."
                    else
                        "Xác nhận thao tác giao cho MVĐ $firstCode? Nạp lại MVĐ sẽ xóa dấu này."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentIndex = numberedRouteGroups.indexOfFirst { it.key == group.key }
                    val nextOrder = if (currentIndex >= 0) {
                        numberedRouteGroups.drop(currentIndex + 1)
                            .firstOrNull { next -> next.orders.any { !it.locallyDelivered && !isTerminalOrderStatus(it.status) } }
                            ?.orders?.firstOrNull()
                    } else null
                    group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) }
                    pendingDeliveredGroup = null
                    nextOrder?.let { next ->
                        deliveryFocusCode = next.code
                        onDeliveryFocusNext(next)
                    }
                }) { Text("XÁC NHẬN", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeliveredGroup = null }) { Text("HỦY") }
            }
        )
    }

    if (editPicker) {
        val editQuery = keyword.trim()
        val editCandidates = vm.orders.filter { o ->
            editQuery.isBlank() || o.code.contains(editQuery, true) || o.customer.contains(editQuery, true) ||
                o.phone.contains(editQuery, true) || o.address.contains(editQuery, true) ||
                o.shop.contains(editQuery, true) || o.item.contains(editQuery, true) || matchesCodSearch(o.amount, editQuery)
        }
        AlertDialog(
            onDismissRequest = { editPicker = false },
            title = { Text("Chọn đơn cần sửa") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    editCandidates.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editOrder = o; editPicker = false }.padding(vertical = 9.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(o.code, color = Navy, fontWeight = FontWeight.Bold)
                                Text("${o.customer} • ${o.phone}", fontSize = 11.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (editCandidates.isEmpty()) Text("Không tìm thấy đơn phù hợp", color = TextGray, modifier = Modifier.padding(10.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editPicker = false }) { Text("ĐÓNG") } }
        )
    }

    if (showAddOrder) {
        var code by remember { mutableStateOf("") }
        var shop by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var goods by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("TT500") }
        var cod by remember { mutableStateOf("") }
        var services by remember { mutableStateOf("") }
        var statusMenu by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val matchedCustomer = vm.findCustomerByPhone(phone)
        AlertDialog(
            onDismissRequest = { showAddOrder = false },
            title = { Text("Thêm Đơn Hàng") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(code, { code = it; error = "" }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mã Vận Đơn *") }, singleLine = true)
                    OutlinedTextField(shop, { shop = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên Shop") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Số điện thoại") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    matchedCustomer?.let {
                        Text("Khách: ${it.name}\n${it.address}", color = TextGray, fontSize = 12.sp)
                    }
                    OutlinedTextField(goods, { goods = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hàng hóa") })
                    Box {
                        OutlinedButton(onClick = { statusMenu = true }) { Text("TT: $status") }
                        DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                            AllowedOrderStatuses.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value, color = orderStatusColor(value)) },
                                    onClick = { status = value; statusMenu = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(cod, { cod = it; error = "" }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("COD (đ)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(services, { services = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dịch vụ") }, placeholder = { Text("COD, PXD, XMG") })
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanCode = code.trim()
                    val cleanCod = cod.trim()
                    val digits = cleanCod.filter { it in '0'..'9' }
                    when {
                        cleanCode.isBlank() -> error = "Vui lòng nhập mã vận đơn."
                        cleanCode.any(Char::isWhitespace) -> error = "Mã vận đơn không được chứa khoảng trắng."
                        cleanCod.isNotBlank() && (cleanCod.any { it !in "0123456789., " } ||
                            digits.toLongOrNull() == null) -> error = "COD phải là số tiền nguyên không âm, ví dụ 132000."
                        else -> {
                            val customer = vm.findCustomerByPhone(phone)
                            val order = Order(
                                code = cleanCode, shop = shop.trim(), phone = phone.trim(),
                                customer = customer?.name.orEmpty(), address = customer?.address.orEmpty(),
                                item = goods.trim(), amount = (digits.ifBlank { "0" }.toLong()).toString() + "đ",
                                status = status,
                                tags = services.split(',', ';', '|', ' ', '\n', '\t')
                                    .map(String::trim).filter(String::isNotBlank).distinct()
                            )
                            if (vm.addManualOrder(order)) {
                                keyword = ""
                                showAddOrder = false
                                Toast.makeText(context, "Đã thêm MVĐ $cleanCode", Toast.LENGTH_SHORT).show()
                            } else error = "Mã vận đơn này đã có. Hãy dùng Sửa đơn hàng."
                        }
                    }
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { showAddOrder = false }) { Text("HỦY") } }
        )
    }

    editOrder?.let { original ->
        var shop by remember(original.code) { mutableStateOf(original.shop) }
        var customer by remember(original.code) { mutableStateOf(original.customer) }
        var phone by remember(original.code) { mutableStateOf(original.phone) }
        var address by remember(original.code) { mutableStateOf(original.address) }
        var amount by remember(original.code) { mutableStateOf(original.amount) }
        var item by remember(original.code) { mutableStateOf(original.item) }
        var services by remember(original.code) { mutableStateOf(original.tags.joinToString(" ")) }
        AlertDialog(
            onDismissRequest = { editOrder = null },
            title = { Text("Sửa ${original.code}") },
            text = {
                Column(
                    Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(shop, { shop = it }, label = { Text("Tên Shop") }, singleLine = true)
                    OutlinedTextField(customer, { customer = it }, label = { Text("Tên khách") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("SĐT") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Địa chỉ") })
                    OutlinedTextField(amount, { amount = it }, label = { Text("COD") }, singleLine = true)
                    OutlinedTextField(item, { item = it }, label = { Text("Hàng hóa") })
                    OutlinedTextField(services, { services = it }, label = { Text("Dịch vụ") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = services.split(Regex("[\\s,;]+" )).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    vm.updateOrder(original.copy(
                        shop = shop,
                        customer = customer,
                        phone = phone,
                        address = address,
                        amount = amount,
                        item = item,
                        tags = tags
                    ))
                    editOrder = null
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { editOrder = null }) { Text("HỦY") } }
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xóa đơn hàng") },
        text = { Text("Xác nhận xóa ${selected.size} đơn đã chọn?") },
        confirmButton = {
            TextButton(onClick = {
                val clearAllRouteMemory = deleteAllWasChosen && selected.isNotEmpty()
                vm.deleteOrders(selected)
                if (clearAllRouteMemory) vm.clearRouteNumbering()
                selected = emptySet()
                deleteAllWasChosen = false
                confirmDelete = false
                deleteMode = false
            }) { Text("XÓA", color = Color(0xFFE21B1B)) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("HỦY") } }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DeliveryGroupCard(
    routeStt: Int?,
    group: DeliveryGroup,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit,
    onRedeliver: () -> Unit
) {
    val context = LocalContext.current
    val primary = group.orders.first()
    val customer = group.customer
    val phone = (customer?.phone ?: primary.phone).filter(Char::isDigit)

    fun openZalo() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/$phone")))
        }
    }
    fun openSms() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$phone")))
        }
    }
    fun openCall() {
        if (phone.isNotBlank()) runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
        }
    }

    // Chỉ Customer ID có từ 2 MVĐ trở lên mới có phần đầu nhóm.
    if (group.orders.size > 1) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF9DCEA8) else Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // Phần dùng chung: STT - số đơn - tổng tiền.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!delivered && group.orders.any { !isTerminalOrderStatus(it.status) }) {
                        Box(Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() }, contentAlignment = Alignment.Center) {
                            RouteStateCircle(routeStt, deliveryGroupHasCoordinate(group), false)
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${group.orders.size} đơn • Tổng COD", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(groupMoneyText(group), color = MoneyGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Cụm nút thao tác dùng chung cho tất cả MVĐ trong Customer ID này.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        delivered || group.orders.all { normalizeOrderStatus(it.status) == "TT505" } ->
                            ActionButton("Giao lại", Icons.Default.RestartAlt, filled = true, buttonWeight = 1.28f, onClick = onRedeliver)
                        else ->
                            ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)
                    }
                    ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
                    ActionButton("Zalo", Icons.Default.Chat, onClick = { openZalo() })
                    ActionButton("SMS", Icons.Default.Sms, onClick = { openSms() })
                    ActionButton("Gọi", Icons.Default.Call, onClick = { openCall() })
                }

                // Mỗi đơn vẫn giữ nguyên cấu trúc chi tiết riêng như giao diện cũ.
                group.orders.forEachIndexed { index, order ->
                    if (index > 0) HorizontalDivider(color = Border)
                    GroupedOrderDetail(
                        order = order,
                        delivered = delivered,
                        onCustomerClick = onCustomerClick
                    )
                }
            }
        }
    } else {
        // Customer ID chỉ có 1 MVĐ: hiển thị bình thường, không có phần đầu nhóm.
        SingleOrderDetailCard(
            order = primary,
            routeStt = routeStt,
            hasRealCoordinate = deliveryGroupHasCoordinate(group),
            delivered = delivered,
            onNumberClick = onNumberClick,
            onCustomerClick = onCustomerClick,
            onDelivered = onDelivered,
            onRedeliver = onRedeliver,
            onZalo = { openZalo() },
            onSms = { openSms() },
            onCall = { openCall() }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CopyableWaybillCode(
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Text(
        text = code,
        color = Navy,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MVĐ", code))
                Toast.makeText(context, "Đã copy MVĐ $code", Toast.LENGTH_SHORT).show()
            }
        )
    )
}

@Composable
private fun GroupedOrderDetail(
    order: Order,
    delivered: Boolean,
    onCustomerClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrderHeading(order, delivered, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
        OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
        OrderInfoRow(Icons.Default.LocationOn, order.address)
        OrderInfoRow(Icons.Default.Inventory2, order.item)
        if (order.tags.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            OrderTags(order.tags)
        }
    }
}

@Composable
private fun SingleOrderDetailCard(
    order: Order,
    routeStt: Int?,
    hasRealCoordinate: Boolean,
    delivered: Boolean,
    onNumberClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit,
    onRedeliver: () -> Unit,
    onZalo: () -> Unit,
    onSms: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (delivered) Color(0xFFE4F5E8) else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF9DCEA8) else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!delivered && !isTerminalOrderStatus(order.status)) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() },
                        contentAlignment = Alignment.Center
                    ) { RouteStateCircle(routeStt, hasRealCoordinate, false) }
                    Spacer(Modifier.width(7.dp))
                }
                OrderHeading(order, delivered, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn, order.address)
            OrderInfoRow(Icons.Default.Inventory2, order.item)
            if (order.tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                OrderTags(order.tags)
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    delivered || normalizeOrderStatus(order.status) == "TT505" ->
                        ActionButton("Giao lại", Icons.Default.RestartAlt, filled = true, buttonWeight = 1.28f, onClick = onRedeliver)
                    else ->
                        ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, buttonWeight = 1.28f, onClick = onDelivered)
                }
                ActionButton("Bank", Icons.Default.AccountBalance, onClick = {})
                ActionButton("Zalo", Icons.Default.Chat, onClick = onZalo)
                ActionButton("SMS", Icons.Default.Sms, onClick = onSms)
                ActionButton("Gọi", Icons.Default.Call, onClick = onCall)
            }
        }
    }
}

@Composable
fun OrderInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(vertical = 3.dp)
    Row(
        rowModifier,
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = Navy,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            softWrap = true
        )
    }
}

@Composable
private fun WaybillQrButton(code: String) {
    var showQr by remember(code) { mutableStateOf(false) }
    val qrBitmap = remember(code) {
        runCatching {
            com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(
                code,
                com.google.zxing.BarcodeFormat.QR_CODE,
                720,
                720
            )
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(0.5.dp, Color(0xFFD8D8D8), RoundedCornerShape(6.dp))
            .clickable(enabled = qrBitmap != null) { showQr = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.QrCode2,
            contentDescription = "Tạo mã QR cho MVĐ $code",
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showQr && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Mã QR MVĐ", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Mã QR của MVĐ $code",
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(code, color = Navy, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text("ĐÓNG", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OrderHeading(order: Order, delivered: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CopyableWaybillCode(order.code, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(5.dp))
            WaybillQrButton(order.code)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (!delivered) {
                Text(normalizeOrderStatus(order.status), color = orderStatusColor(order.status),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(order.amount, color = MoneyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OrderTags(tags: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        tags.forEach { Tag(it) }
    }
}

@Composable
fun Tag(text: String) {
    val highlighted = text.trim().uppercase() in HighlightServiceCodes
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) Color(0xFFD32F2F) else Color(0xFFEDE9E4))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (highlighted) Color.White else Navy,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RowScope.ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean = false,
    buttonWeight: Float = 1f,
    onClick: () -> Unit = {}
) {
    Box(
        Modifier
            .height(36.dp)
            .weight(buttonWeight)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
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
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
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
    val photoScope = rememberCoroutineScope()
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
        photoScope.launch {
            val stored = storeGatePhoto(context, uri)
            if (stored != null) onPhotoChanged(stored)
            else Toast.makeText(context, "Không lưu được ảnh đã chọn. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
        }
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
                modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                onPhotoClick = { showPhotoMenu = true }
            ) {
                CustomerSideActions(
                    modifier = Modifier.padding(bottom = 3.dp),
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
internal fun CustomerPageHeader(title: String, onBack: () -> Unit) {
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
    modifier: Modifier = Modifier,
    onPhotoClick: () -> Unit,
    actions: @Composable () -> Unit
) {
    val photo by rememberGatePhoto(customer.photoUri)
    val bitmap = photo.bitmap
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onPhotoClick).then(
                if (bitmap != null) Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                else Modifier.height(306.dp)
            ), RoundedCornerShape(14.dp),
            CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), "Cổng nhà khách", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else if (photo.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storefront, "Cổng nhà khách", tint = Orange, modifier = Modifier.size(122.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (customer.photoUri.isBlank()) "Hình cổng nhà khách" else "Không đọc được ảnh. Chạm để chọn lại.",
                            color = TextGray, fontSize = 13.sp
                        )
                    }
                }

            }
        }
        // Actions and information share the same scrolling block below the full photo.
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (listOf(customer.name) + customer.aliases).filter { it.isNotBlank() }.forEach { displayName ->
                    Box(Modifier.padding(start = 50.dp)) { DetailInfoCard(Icons.Default.Person, displayName) }
                }
                (listOf(customer.phone) + customer.extraPhones.map { it.number }).filter { it.isNotBlank() }.forEach { displayPhone ->
                    Box(Modifier.padding(start = 50.dp)) { DetailInfoCard(Icons.Default.Phone, displayPhone) }
                }
                if (customer.streetName.isNotBlank()) {
                    Box(Modifier.padding(start = 50.dp)) {
                        DetailInfoCard(Icons.Default.Map, "Tên đường: ${customer.streetName}")
                    }
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
            actions()
        }
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
private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean, val photoUri: String = "")

@Composable
fun CustomerFormScreen(
    customer: Customer?,
    streetNameOptions: List<String> = emptyList(),
    onStreetNameCreated: (String) -> Unit = {},
    onBack: () -> Unit,
    onSave: (Customer, Customer?) -> Unit
) {
    val original = remember(customer?.id) { customer }
    var pendingCoordinateSave by remember(customer?.id) { mutableStateOf<Customer?>(null) }
    var coordinateError by remember(customer?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var streetName by remember(customer?.id) { mutableStateOf(customer?.streetName.orEmpty()) }
    var note by remember(customer?.id) { mutableStateOf(customer?.note.orEmpty()) }
    val addresses = remember(customer?.id) {
        mutableStateListOf<AddressDraft>().apply {
            if (customer == null) add(AddressDraft("", "", "", true)) else {
                add(AddressDraft(customer.address, customer.latitude, customer.longitude, true, customer.photoUri))
                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false, it.photoUri) })
            }
        }
    }
    val photoUri = addresses.firstOrNull { it.isPrimary }?.photoUri.orEmpty()
    var importingPhoto by remember { mutableStateOf(false) }
    val photoScope = rememberCoroutineScope()
    fun setPrimaryPhoto(uri: String) {
        val index = addresses.indexOfFirst { it.isPrimary }
        if (index >= 0) addresses[index] = addresses[index].copy(photoUri = uri)
    }
    val names = remember(customer?.id) { mutableStateListOf<String>().apply { add(customer?.name.orEmpty()); addAll(customer?.aliases.orEmpty()) } }
    var expandedPhone by remember { mutableStateOf<Int?>(null) }
    var pickAddressIndex by remember { mutableStateOf<Int?>(null) }
    var validation by remember { mutableStateOf(false) }
    var showFormPhotoMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        importingPhoto = true
        photoScope.launch {
            try {
                val stored = storeGatePhoto(context, uri)
                if (stored != null) setPrimaryPhoto(stored)
                else Toast.makeText(context, "Không lưu được ảnh đã chọn. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
            } finally { importingPhoto = false }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCameraUri?.let { setPrimaryPhoto(it.toString()) }
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


    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader(if (customer == null) "THÊM KHÁCH HÀNG" else "SỬA KHÁCH HÀNG", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                CustomerPhotoCard(photoUri)
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp).size(38.dp).clickable(enabled = !importingPhoto) { showFormPhotoMenu = true }, CircleShape, color = Orange) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, "Quản lý ảnh", tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
            }
            if (importingPhoto) Text("Đang lưu ảnh…", color = TextGray, fontSize = 12.sp)
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
            StreetNameDropdown(
                value = streetName,
                onValueChange = { streetName = it },
                options = streetNameOptions,
                onCreate = onStreetNameCreated
            )
            addresses.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.isPrimary, enabled = !importingPhoto, onCheckedChange = { checked -> if (checked) addresses.indices.forEach { i -> addresses[i] = addresses[i].copy(isPrimary = i == index) } }, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(3.dp))
                            OutlinedTextField(value = item.address, onValueChange = { addresses[index] = item.copy(address = it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Địa chỉ", fontSize = 12.sp) })
                            SmallPlusMinus(plus = true) { addresses.add(index + 1, AddressDraft("", "", "", false)) }
                            if (addresses.size > 1 && !importingPhoto) SmallPlusMinus(plus = false) {
                                val wasPrimary = addresses[index].isPrimary; addresses.removeAt(index)
                                if (wasPrimary && addresses.isNotEmpty()) addresses[0] = addresses[0].copy(isPrimary = true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fun setCoord(raw: String, latitudeField: Boolean) {
                                val (lat, lng) = CustomerCoordinateSafety.input(raw, latitudeField, addresses[index].latitude, addresses[index].longitude)
                                addresses[index] = addresses[index].copy(latitude = lat, longitude = lng)
                            }
                            OutlinedTextField(item.latitude, { setCoord(it, true) }, Modifier.weight(1f), singleLine = true, label = { Text("Vĩ độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(item.longitude, { setCoord(it, false) }, Modifier.weight(1f), singleLine = true, label = { Text("Kinh độ", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            TextButton(onClick = { pickAddressIndex = index }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(2.dp)); Text("Chọn trên bản đồ", fontSize = 11.sp) }
                        }
                    }
                }
            }

            CompactInput("Ghi chú", note, { note = it }, "Ghi chú", singleLine = false, minLines = 2)
            if (coordinateError) Text("Tọa độ phải đủ cả vĩ độ và kinh độ hợp lệ, hoặc để trống cả hai.", color = Color(0xFFE21B1B), fontSize = 12.sp)
            if (validation) Text("Cần nhập tên, ít nhất 1 SĐT và 1 địa chỉ.", color = Color(0xFFE21B1B), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                val validPhones = phones.filter { it.number.isNotBlank() }; val validAddresses = addresses.filter { it.address.isNotBlank() }
                if (name.isBlank() || validPhones.isEmpty() || validAddresses.isEmpty()) validation = true else {
                    val primaryAddress = validAddresses.firstOrNull { it.isPrimary } ?: validAddresses.first(); val primaryPhone = validPhones.first()
                    val edited = Customer(id = customer?.id ?: 0L, name = name.trim(), phone = primaryPhone.number.trim(), address = primaryAddress.address.trim(), streetName = streetName.trim(), latitude = primaryAddress.latitude.trim(), longitude = primaryAddress.longitude.trim(), initials = createInitials(name), aliases = names.drop(1).map { it.trim() }.filter { it.isNotBlank() }, extraPhones = validPhones.drop(1).map { CustomerPhone(it.number.trim(), if (it.canCall) "Gọi" else if (it.canZalo) "Zalo" else "SMS", it.canCall, it.canZalo, it.canSms) }, extraAddresses = validAddresses.filter { it !== primaryAddress }.map { CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false, it.photoUri) }, note = note.trim(), primaryCanCall = primaryPhone.canCall, primaryCanZalo = primaryPhone.canZalo, primaryCanSms = primaryPhone.canSms, photoUri = primaryAddress.photoUri)
                    // Existing malformed legacy coordinates may be preserved by unrelated edits.
                    val positions = CustomerCoordinateSafety.positions(edited)
                    val oldPositions = original?.let(CustomerCoordinateSafety::positions).orEmpty()
                    coordinateError = positions.any { (lat, lng) ->
                        (lat.isNotBlank() || lng.isNotBlank()) && !CustomerCoordinateSafety.valid(lat, lng) && (lat to lng) !in oldPositions
                    }
                    if (!coordinateError) {
                        if (original != null && positions != oldPositions) pendingCoordinateSave = edited
                        else onSave(edited, original)
                    }
                }
            }, enabled = !importingPhoto, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("LƯU", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onBack, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(14.dp)) { Text("Hủy") }
        }
    }

    pendingCoordinateSave?.let { edited ->
        fun describe(value: Customer): String = CustomerCoordinateSafety.positions(value).mapIndexed { index, (lat, lng) ->
            "${if (index == 0) "Chính" else "Phụ $index"}: ${if (lat.isBlank() && lng.isBlank()) "Chưa có tọa độ" else "$lat, $lng"}"
        }.joinToString("\n")
        AlertDialog(
            onDismissRequest = { pendingCoordinateSave = null },
            title = { Text("Xác nhận đổi tọa độ khách") },
            text = { Text("Đã lưu:\n${describe(requireNotNull(original))}\n\nSẽ lưu:\n${describe(edited)}", modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { pendingCoordinateSave = null; onSave(edited, original) }) { Text("Đổi tọa độ và lưu") } },
            dismissButton = { TextButton(onClick = { pendingCoordinateSave = null }) { Text("Quay lại kiểm tra") } }
        )
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
            FilledTonalButton(onClick = { setPrimaryPhoto(""); showFormPhotoMenu = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE21B1B)); Spacer(Modifier.width(6.dp)); Text("Xóa ảnh", color = Color(0xFFE21B1B)) }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { showFormPhotoMenu = false }) { Text("Hủy") } }
    )

    pickAddressIndex?.let { index ->
        CustomerCoordinateMapPicker(initialPoint = pointFromStrings(addresses[index].latitude, addresses[index].longitude), focusUserLocation = true, onDismiss = { pickAddressIndex = null }, onSavePoint = { point ->
            addresses[index] = addresses[index].copy(latitude = "%.6f".format(java.util.Locale.US, point.latitude), longitude = "%.6f".format(java.util.Locale.US, point.longitude)); pickAddressIndex = null
        })
    }
}

@Composable
private fun StreetNameDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onCreate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val typed = value.trim()
    val available = options
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(java.util.Locale.getDefault()) }
    val filtered = available.filter { typed.isBlank() || it.contains(typed, ignoreCase = true) }
    val hasExactMatch = available.any { it.equals(typed, ignoreCase = true) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tên Đường", fontSize = 11.sp) },
            placeholder = { Text("Nhập hoặc chọn tên đường", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        "Mở danh sách tên đường"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onValueChange(typed)
                if (typed.isNotBlank() && !hasExactMatch) onCreate(typed)
                expanded = false
            })
        )
        DropdownMenu(
            expanded = expanded && (filtered.isNotEmpty() || typed.isNotBlank()),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 220.dp),
            properties = PopupProperties(focusable = false)
        ) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
            if (typed.isNotBlank() && !hasExactMatch) {
                if (filtered.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("+ Tạo \"$typed\"", color = Orange, fontWeight = FontWeight.Bold) },
                    onClick = {
                        onValueChange(typed)
                        onCreate(typed)
                        expanded = false
                    }
                )
            }
        }
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
    val photo by rememberGatePhoto(photoUri)
    val bitmap = photo.bitmap
    Card(modifier = Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), "Cổng nhà khách", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (photo.loading) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, "Ảnh khách hàng", tint = Orange, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(if (photoUri.isBlank()) "Hình cổng nhà khách" else "Không đọc được ảnh. Bấm nút ảnh để chọn lại.", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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


// ================================================================
// 10. Ô TÌM KIẾM + SỐ THỨ TỰ
// ================================================================
@Composable
fun SearchBox(value:String,onValueChange:(String)->Unit,onClear:()->Unit,onQrClick:()->Unit){
    OutlinedTextField(
        value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
        placeholder={Text("Tìm mã đơn, tên, COD...",color=TextGray,fontSize=14.sp,maxLines=1)},
        leadingIcon={Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Orange).clickable{onQrClick()},contentAlignment=Alignment.Center){Icon(Icons.Default.QrCodeScanner,"Quét QR",tint=Color.White,modifier=Modifier.size(23.dp))}},
        trailingIcon={if(value.isNotBlank()) IconButton(onClick=onClear){Icon(Icons.Default.Close,"Xóa",tint=TextGray,modifier=Modifier.size(20.dp))}},
        shape=RoundedCornerShape(14.dp),singleLine=true,
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Orange,unfocusedBorderColor=Border,focusedContainerColor=Color(0xFFF7F5F2),unfocusedContainerColor=Color(0xFFF7F5F2),cursorColor=OrangeDark))
}

@Composable
internal fun RouteStateCircle(number: Int?, hasCoordinate: Boolean, selected: Boolean) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(
            when {
                selected -> Color(0xFF168DE2)
                hasCoordinate -> Orange
                else -> Color(0xFF8995A5)
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        if (number != null) Text(number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ================================================================
// 11. VIEWMODEL
// ================================================================
class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE)
    var routeNumberingEnabled by mutableStateOf(prefs.getBoolean("route_numbering_enabled_v1", false))
        private set
    private var routeAnchorLat by mutableStateOf(prefs.getString("route_anchor_lat_v1", "").orEmpty())
    private var routeAnchorLng by mutableStateOf(prefs.getString("route_anchor_lng_v1", "").orEmpty())

    // STT tuyến được lưu tách khỏi vị trí phần tử trong orderState.
    // Giao hàng / xóa đơn tuyệt đối không dồn STT; chỉ các thao tác sửa/tạo tuyến mới ghi lại bảng này.
    private val routeSttState = mutableStateMapOf<String, Int>().apply {
        val obj = runCatching {
            JSONObject(prefs.getString("route_stt_map_v1", "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val value = obj.optInt(code, 0)
            if (value > 0) put(code, value)
        }
    }

    private fun persistRouteStt() {
        val obj = JSONObject()
        routeSttState.forEach { (code, stt) -> obj.put(code, stt) }
        prefs.edit().putString("route_stt_map_v1", obj.toString()).apply()
    }

    fun routeSttFor(code: String): Int? = routeSttState[code]

    fun routeSttForGroup(codes: List<String>): Int? =
        codes.mapNotNull { routeSttState[it] }.minOrNull()

    fun ensureRouteSttGroups(groups: List<List<String>>) {
        if (!routeNumberingEnabled) return
        var changed = false
        var next = (routeSttState.values.maxOrNull() ?: 0) + 1
        groups.forEach { codes ->
            val existing = codes.mapNotNull { routeSttState[it] }.minOrNull()
            val stt = existing ?: next++
            codes.forEach { code ->
                if (routeSttState[code] == null) {
                    routeSttState[code] = stt
                    changed = true
                }
            }
        }
        if (changed) persistRouteStt()
    }

    fun replaceRouteStt(groups: List<List<String>>) {
        routeSttState.clear()
        groups.forEachIndexed { index, codes ->
            codes.forEach { code -> routeSttState[code] = index + 1 }
        }
        persistRouteStt()
    }

    fun routeAnchorPoint(): MapPoint? {
        routeAnchorLat = prefs.getString("route_anchor_lat_v1", routeAnchorLat).orEmpty()
        routeAnchorLng = prefs.getString("route_anchor_lng_v1", routeAnchorLng).orEmpty()
        return pointFromStrings(routeAnchorLat, routeAnchorLng)
    }

    fun enableRouteNumbering() {
        routeNumberingEnabled = true
        prefs.edit().putBoolean("route_numbering_enabled_v1", true).apply()
    }

    fun clearRouteNumbering() {
        routeNumberingEnabled = false
        routeSttState.clear()
        prefs.edit()
            .putBoolean("route_numbering_enabled_v1", false)
            .remove("route_stt_map_v1")
            .apply()
    }


    // Independent, versioned store: no order/customer/STT fields are changed by learning.
    internal fun streetLearningSamples(): List<StreetRouteLearning.Sample> = customerState.mapNotNull { c ->
        val p = pointFromStrings(c.latitude, c.longitude) ?: return@mapNotNull null
        c.streetName.takeIf(String::isNotBlank) ?: return@mapNotNull null
        StreetRouteLearning.Sample(c.id, c.streetName, StreetRouteLearning.Point(p.latitude, p.longitude))
    }

    internal fun streetLearningHistory(): List<List<StreetRouteLearning.Visit>> = runCatching {
        val root = JSONObject(prefs.getString("street_route_lessons_v1", "{}") ?: "{}")
        val routes = root.optJSONArray("routes") ?: org.json.JSONArray()
        (maxOf(0, routes.length() - 24) until routes.length()).mapNotNull { i ->
            val route = routes.optJSONArray(i) ?: return@mapNotNull null
            if (route.length() > 2000) return@mapNotNull null
            (0 until route.length()).map { j ->
                val v = route.optJSONObject(j)
                StreetRouteLearning.Visit(
                    v?.optLong("id", 0L) ?: 0L, v?.optString("street", "").orEmpty(),
                    StreetRouteLearning.Point(v?.optDouble("lat", 0.0) ?: 0.0, v?.optDouble("lng", 0.0) ?: 0.0)
                )
            }
        }
    }.getOrDefault(emptyList())

    internal fun legacyRouteLearningSnapshot(): Map<String, Double> = runCatching {
        val root = JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}")
        val result = mutableMapOf<String, Double>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = root.optDouble(key, 0.0)
            if (value.isFinite()) result[key] = value
        }
        result.toMap()
    }.getOrDefault(emptyMap())

    fun learnStreetRoute(codes: List<String>) {
        // Called only by the user's Save route actions, never by create/deliver/delete/import.
        runCatching {
            val groups = buildDeliveryGroups(orderState, customerState)
            val byCode = groups.flatMap { g -> g.orders.map { it.code to g } }.toMap()
            val visits = codes.map { code ->
                val g = byCode[code]
                val c = g?.customer
                val p = g?.let(::deliveryGroupPoint)
                StreetRouteLearning.Visit(
                    c?.id ?: 0L, c?.streetName.orEmpty(),
                    StreetRouteLearning.Point(p?.latitude ?: 0.0, p?.longitude ?: 0.0)
                )
            }
            val history = StreetRouteLearning.rememberRoute(streetLearningHistory(), visits)
            val routes = org.json.JSONArray()
            history.forEach { route ->
                val row = org.json.JSONArray()
                route.forEach { v -> row.put(JSONObject().apply {
                    put("id", v.customerId); put("street", v.street)
                    put("lat", v.point.lat); put("lng", v.point.lng)
                }) }
                routes.put(row)
            }
            prefs.edit().putString("street_route_lessons_v1",
                JSONObject().put("version", 1).put("routes", routes).toString()).apply()
        }.onFailure { android.util.Log.w("GiaoHangPro", "Could not save street route lesson", it) }
    }

    fun learnRoutePattern(points: List<MapPoint>) {
        if (points.size < 2) return
        val obj = runCatching { JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val scale = 500.0
        fun cell(p: MapPoint) = "${kotlin.math.floor(p.latitude * scale).toInt()},${kotlin.math.floor(p.longitude * scale).toInt()}"
        for (i in 0 until points.lastIndex) {
            val key = "${cell(points[i])}>${cell(points[i + 1])}"
            obj.put(key, obj.optDouble(key, 0.0) + 1.0)
        }
        prefs.edit().putString("route_learning_v1", obj.toString()).apply()
    }

    private val orderState = mutableStateListOf<Order>()
    val orders: List<Order> get() = orderState
    private val customerState = mutableStateListOf<Customer>()
    val customers: List<Customer> get() = customerState
    private val streetNameState = mutableStateListOf<String>().apply {
        val stored = prefs.getString("street_names_v1", null)
        if (!stored.isNullOrBlank()) {
            runCatching {
                val array = org.json.JSONArray(stored)
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf(String::isNotBlank)?.let { name ->
                        if (none { it.equals(name, ignoreCase = true) }) add(name)
                    }
                }
            }
        }
    }
    val streetNames: List<String> get() = streetNameState

    init {
        val loaded = loadPersistentData()
        if (!loaded) {
            orderState.addAll(sampleOrders)
            customerState.addAll(sampleCustomers)
            savePersistentData()
        }
        var streetNamesChanged = false
        customerState.map { it.streetName.trim() }.filter(String::isNotBlank).forEach { name ->
            if (streetNameState.none { it.equals(name, ignoreCase = true) }) {
                streetNameState.add(name)
                streetNamesChanged = true
            }
        }
        if (streetNamesChanged) persistStreetNames()
        if (routeNumberingEnabled) {
            val groups = buildDeliveryGroups(
                orderState.filterNot { isTerminalOrderStatus(it.status) },
                customerState
            )
            ensureRouteSttGroups(groups.map { g -> g.orders.map(Order::code) })
        }
    }

    private fun optString(o: org.json.JSONObject, key: String): String = if (o.has(key) && !o.isNull(key)) o.optString(key, "") else ""

    private fun loadPersistentData(): Boolean = runCatching {
        val ordersJson = prefs.getString("orders", null)
        val customersJson = prefs.getString("customers", null)
        if (ordersJson == null && customersJson == null) return@runCatching false
        orderState.clear(); customerState.clear()
        ordersJson?.let { raw ->
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tagsArr = o.optJSONArray("tags") ?: org.json.JSONArray()
                val tags = buildList { for (j in 0 until tagsArr.length()) add(tagsArr.optString(j)) }
                orderState.add(Order(
                    code=optString(o,"code"), customer=optString(o,"customer"), phone=optString(o,"phone"),
                    address=optString(o,"address"), item=optString(o,"item"), amount=optString(o,"amount"), tags=tags,
                    shop=optString(o,"shop"), status=normalizeOrderStatus(optString(o,"status")),
                    latitude=optString(o,"latitude"), longitude=optString(o,"longitude"),
                    locallyDelivered=o.optBoolean("locallyDelivered", false) || optString(o,"status").equals("Đã giao", true)
                ))
            }
        }
        customersJson?.let { raw ->
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val aliasesArr=o.optJSONArray("aliases") ?: org.json.JSONArray()
                val aliases=buildList { for(j in 0 until aliasesArr.length()) add(aliasesArr.optString(j)) }
                val phonesArr=o.optJSONArray("extraPhones") ?: org.json.JSONArray()
                val phones=buildList {
                    for(j in 0 until phonesArr.length()) {
                        val p=phonesArr.getJSONObject(j)
                        add(CustomerPhone(optString(p,"number"),optString(p,"action").ifBlank{"Gọi"},p.optBoolean("canCall",true),p.optBoolean("canZalo",true),p.optBoolean("canSms",true)))
                    }
                }
                val addrArr=o.optJSONArray("extraAddresses") ?: org.json.JSONArray()
                val addresses=buildList {
                    for(j in 0 until addrArr.length()) {
                        val a=addrArr.getJSONObject(j)
                        add(CustomerAddress(optString(a,"address"),optString(a,"latitude"),optString(a,"longitude"),a.optBoolean("isPrimary",false),optString(a,"photoUri")))
                    }
                }
                customerState.add(Customer(
                    id=o.optLong("id",0L), name=optString(o,"name"), phone=optString(o,"phone"), address=optString(o,"address"),
                    streetName=optString(o,"streetName"), latitude=optString(o,"latitude"), longitude=optString(o,"longitude"), initials=optString(o,"initials"), aliases=aliases,
                    extraPhones=phones, extraAddresses=addresses, note=optString(o,"note"),
                    primaryCanCall=o.optBoolean("primaryCanCall",true), primaryCanZalo=o.optBoolean("primaryCanZalo",true),
                    primaryCanSms=o.optBoolean("primaryCanSms",true), photoUri=optString(o,"photoUri")
                ))
            }
        }
        true
    }.getOrElse { false }

    private fun savePersistentData(editor: android.content.SharedPreferences.Editor = prefs.edit()) {
        val ordersArr=org.json.JSONArray()
        orderState.forEach { o ->
            ordersArr.put(org.json.JSONObject().apply {
                put("code",o.code); put("customer",o.customer); put("phone",o.phone); put("address",o.address); put("item",o.item)
                put("amount",o.amount); put("tags",org.json.JSONArray(o.tags)); put("shop",o.shop); put("status",o.status)
                put("latitude",o.latitude); put("longitude",o.longitude)
                put("locallyDelivered",o.locallyDelivered)
            })
        }
        val customersArr=org.json.JSONArray()
        customerState.forEach { c ->
            customersArr.put(org.json.JSONObject().apply {
                put("id",c.id); put("name",c.name); put("phone",c.phone); put("address",c.address); put("streetName",c.streetName); put("latitude",c.latitude); put("longitude",c.longitude)
                put("initials",c.initials); put("aliases",org.json.JSONArray(c.aliases)); put("note",c.note)
                put("primaryCanCall",c.primaryCanCall); put("primaryCanZalo",c.primaryCanZalo); put("primaryCanSms",c.primaryCanSms); put("photoUri",c.photoUri)
                put("extraPhones",org.json.JSONArray().apply { c.extraPhones.forEach { p -> put(org.json.JSONObject().apply { put("number",p.number); put("action",p.action); put("canCall",p.canCall); put("canZalo",p.canZalo); put("canSms",p.canSms) }) } })
                put("extraAddresses",org.json.JSONArray().apply { c.extraAddresses.forEach { a -> put(org.json.JSONObject().apply { put("address",a.address); put("latitude",a.latitude); put("longitude",a.longitude); put("isPrimary",a.isPrimary); put("photoUri",a.photoUri) }) } })
            })
        }
        editor.putString("orders",ordersArr.toString()).putString("customers",customersArr.toString()).apply()
    }

    fun findCustomer(id: Long): Customer? = customerState.firstOrNull { it.id == id }

    fun reloadPersistentData() { loadPersistentData() }

    private fun normalizeCustomerPhone(raw: String): String = CustomerPhoneLookup.normalize(raw)

    fun findCustomerByPhone(phone: String): Customer? = CustomerPhoneLookup(customerState.toList()).find(phone)

    fun ensureCustomerFromImportedOrder(name: String, phone: String, address: String): Customer? {
        val normalized = normalizeCustomerPhone(phone)
        if (normalized.isBlank()) return null
        findCustomerByPhone(phone)?.let { return it }
        val cleanName = name.trim().ifBlank { phone.trim() }
        val newId = (customerState.maxOfOrNull { it.id } ?: 0L) + 1L
        val created = Customer(id=newId,name=cleanName,phone=phone.trim(),address=address.trim(),initials=createInitials(cleanName))
        customerState.add(created); savePersistentData(); return created
    }

    private fun persistStreetNames() {
        prefs.edit().putString("street_names_v1", org.json.JSONArray(streetNameState).toString()).apply()
    }

    fun addStreetName(streetName: String) {
        val clean = streetName.trim()
        if (clean.isBlank() || streetNameState.any { it.equals(clean, ignoreCase = true) }) return
        streetNameState.add(clean)
        persistStreetNames()
    }

    fun addCustomer(customer: Customer): Long {
        val newId=(customerState.maxOfOrNull { it.id } ?: 0L)+1L
        addStreetName(customer.streetName)
        customerState.add(customer.copy(id=newId)); savePersistentData(); return newId
    }

    fun updateCustomerPhoto(id: Long, uri: String) {
        val index = customerState.indexOfFirst { it.id == id }
        if (index < 0) return
        customerState[index] = CustomerCoordinateSafety.updatePhoto(customerState[index], uri)
        savePersistentData()
    }

    fun updateCustomer(updatedCustomer: Customer, original: Customer): Boolean {
        val index = customerState.indexOfFirst { it.id == updatedCustomer.id }
        if (index < 0 || !CustomerCoordinateSafety.canSave(customerState[index], original)) return false
        addStreetName(updatedCustomer.streetName)
        customerState[index] = updatedCustomer
        savePersistentData()
        return true
    }

    fun deleteCustomer(id: Long) { if(customerState.removeAll { it.id==id }) savePersistentData() }

    fun renameStreetName(oldName: String, newName: String): String? {
        val change = try {
            StreetNameChange.prepare(oldName, newName, streetNameState.toList(), customerState.toList(), streetLearningHistory())
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        val routes = org.json.JSONArray()
        change.lessons.forEach { route ->
            val row = org.json.JSONArray()
            route.forEach { v -> row.put(JSONObject().apply {
                put("id", v.customerId); put("street", v.street)
                put("lat", v.point.lat); put("lng", v.point.lng)
            }) }
            routes.put(row)
        }
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            streetNameState.clear()
            streetNameState.addAll(change.names)
            change.customers.forEachIndexed { i, customer -> customerState[i] = customer }
        }
        // Persist names, assigned customers and renamed learning labels together.
        savePersistentData(prefs.edit()
            .putString("street_names_v1", org.json.JSONArray(change.names).toString())
            .putString("street_route_lessons_v1", JSONObject().put("version", 1).put("routes", routes).toString()))
        return null
    }

    fun deleteStreetName(streetName: String) {
        val target = streetName.trim()
        if (target.isBlank()) return
        val registryChanged = streetNameState.removeAll { it.trim().equals(target, ignoreCase = true) }
        var customerChanged = false
        customerState.indices.forEach { index ->
            val customer = customerState[index]
            if (customer.streetName.trim().equals(target, ignoreCase = true)) {
                customerState[index] = customer.copy(streetName = "")
                customerChanged = true
            }
        }
        if (registryChanged) persistStreetNames()
        if (customerChanged) savePersistentData()
    }

    fun importOrdersCsv(text: String): Int {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return 0
        fun cells(line: String): List<String> {
            val out = mutableListOf<String>(); val b = StringBuilder(); var quoted = false; var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') { b.append('"'); i++ } else quoted = !quoted
                } else if (c == ',' && !quoted) { out += b.toString().trim(); b.setLength(0) } else b.append(c)
                i++
            }
            out += b.toString().trim(); return out
        }
        val h = cells(lines.first()).map {
            java.text.Normalizer.normalize(it.lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace("đ", "d")
        }
        fun idx(vararg keys: String) = h.indexOfFirst { x -> keys.any { x.contains(it) } }
        val ic = idx("mvd", "ma van don"); if (ic < 0) return 0
        val ishop = idx("shop"); val ip = idx("sdt", "so dien thoai"); val iname = idx("ten khach")
        val icod = idx("cod"); val ia = idx("dia chi"); val ii = idx("hang hoa"); val ist = idx("trang thai"); val isv = idx("dich vu")
        var n = 0
        lines.drop(1).forEach { line ->
            val c = cells(line)
            fun g(i: Int) = if (i >= 0 && i < c.size) c[i] else ""
            val code = g(ic).trim()
            if (code.isNotBlank()) {
                val o = Order(
                    code, g(iname), g(ip), g(ia), g(ii), g(icod),
                    g(isv).split(' ', ';', ',', '|').map(String::trim).filter(String::isNotBlank),
                    shop = g(ishop), status = normalizeOrderStatus(g(ist))
                )
                val k = orderState.indexOfFirst { it.code.equals(code, true) }
                if (k >= 0) orderState[k] = o else orderState.add(o)
                n++
            }
        }
        if (n > 0) savePersistentData()
        return n
    }

    fun deleteOrders(codes: Set<String>) {
        if (orderState.removeAll { it.code in codes }) savePersistentData()
    }

    fun addManualOrder(order: Order): Boolean {
        val code = order.code.trim()
        if (code.isBlank() || orderState.any { it.code.equals(code, ignoreCase = true) }) return false
        orderState.add(order.copy(code = code, status = normalizeOrderStatus(order.status), locallyDelivered = false))
        savePersistentData()
        return true
    }

    fun updateOrder(updated: Order) {
        val i=orderState.indexOfFirst { it.code==updated.code }
        if(i>=0){ orderState[i]=updated; savePersistentData() }
    }

    fun markDeliveredGroup(code: String) {
        val base = orderState.firstOrNull { it.code == code } ?: return
        if (base.locallyDelivered || isTerminalOrderStatus(base.status)) return
        val group = buildDeliveryGroups(orderState, customerState)
            .firstOrNull { g -> g.orders.any { it.code == code } } ?: return
        val codes = group.orders.map { it.code }.toSet()
        orderState.indices.forEach { i ->
            if (orderState[i].code in codes) {
                orderState[i] = orderState[i].copy(locallyDelivered = true)
            }
        }
        savePersistentData()
    }

    // Chỉ dùng bởi nút "Giao lại" ở tab Chi tiết đơn.
    // Đơn vừa được đánh dấu "Đã giao" giữ nguyên STT cũ trong routeSttState, nên chỉ cần bỏ dấu cục bộ.
    // Đơn nạp vào sẵn TT505 chưa có STT thì được gán STT mới ở cuối tuyến.
    fun redeliverGroup(code: String) {
        val group = buildDeliveryGroups(orderState, customerState)
            .firstOrNull { g -> g.orders.any { it.code == code } } ?: return
        val codes = group.orders.map { it.code }
        val wasLocallyDelivered = group.orders.any { it.locallyDelivered }

        orderState.indices.forEach { i ->
            if (orderState[i].code in codes) {
                val order = orderState[i]
                orderState[i] = order.copy(
                    locallyDelivered = false,
                    status = if (!wasLocallyDelivered && normalizeOrderStatus(order.status) == "TT505") "TT500" else order.status
                )
            }
        }

        if (!wasLocallyDelivered && codes.none { routeSttState[it] != null }) {
            val nextStt = (routeSttState.values.maxOrNull() ?: 0) + 1
            codes.forEach { routeSttState[it] = nextStt }
            persistRouteStt()
            if (!routeNumberingEnabled) enableRouteNumbering()
        }
        savePersistentData()
    }

    fun reorderOrders(codes: List<String>) {
        val rank = codes.withIndex().associate { it.value to it.index }
        val old = orderState.toList()
        orderState.clear()
        orderState.addAll(old.sortedWith(compareBy<Order> { rank[it.code] ?: Int.MAX_VALUE }.thenBy { old.indexOf(it) }))
        savePersistentData()
    }

    fun importVtmanRecords(records: List<com.example.giaohangpro.vtman.VtmanOrderRecord>) {
        var changed=false
        records.forEach { r ->
            val order=Order(code=r.waybill,customer=r.customer,phone=r.phone,address=r.address,item=r.goods,amount=r.cod,
                tags=r.service.split(',', ' ').map(String::trim).filter(String::isNotBlank),shop=r.shop,status=normalizeOrderStatus(r.status))
            val index=orderState.indexOfFirst { it.code==r.waybill }
            if(index>=0) orderState[index]=order else orderState.add(order)
            ensureCustomerFromImportedOrder(r.customer,r.phone,r.address)
            changed=true
        }
        if(changed) savePersistentData()
    }

    suspend fun importCustomerBackup(context: android.content.Context, uri: android.net.Uri): CustomerBackupResult {
        val existing = customerState.toList()
        val pair = withContext(Dispatchers.IO) {
            val tempRoot = java.io.File(context.cacheDir, "customer_backup_import_${System.currentTimeMillis()}").apply { mkdirs() }
            var customersBytes: ByteArray? = null
            java.util.zip.ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("Không mở được file ZIP")).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    val clean = entry.name.replace('\\', '/').trimStart('/')
                    if (!entry.isDirectory && clean == "customers.json") {
                        customersBytes = zin.readBytes()
                    } else if (!entry.isDirectory && clean.startsWith("photos/customer_gate/")) {
                        val name = java.io.File(clean).name
                        if (name.isNotBlank()) {
                            java.io.File(tempRoot, name).outputStream().use { out -> zin.copyTo(out) }
                        }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            val raw = customersBytes ?: error("ZIP không có customers.json")
            val arr = org.json.JSONArray(String(raw, Charsets.UTF_8))
            val merged = existing.toMutableList()
            var nextId = (merged.maxOfOrNull { it.id } ?: 0L) + 1L
            var added = 0
            var updated = 0
            var imageCount = 0
            var missingImages = 0

            fun normPhone(v: String): String {
                val digits = v.filter(Char::isDigit)
                return when {
                    digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
                    digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
                    else -> digits
                }
            }
            fun normText(v: String): String = v.trim().lowercase(java.util.Locale.getDefault()).replace(Regex("\\s+"), " ")
            fun methods(p: org.json.JSONObject): Triple<Boolean, Boolean, Boolean> {
                val a = p.optJSONArray("methods") ?: org.json.JSONArray()
                val set = buildSet { for (x in 0 until a.length()) add(a.optString(x).uppercase()) }
                return Triple("CALL" in set, "ZALO" in set, "SMS" in set)
            }
            fun importPhoto(path: String?): String {
                if (path.isNullOrBlank()) return ""
                val source = java.io.File(tempRoot, java.io.File(path).name)
                if (!source.exists()) { missingImages++; return "" }
                val ext = source.extension.ifBlank { "webp" }
                val dest = java.io.File(context.filesDir, "customer_gate_import_${System.currentTimeMillis()}_${imageCount}.${ext}")
                source.copyTo(dest, overwrite = true)
                imageCount++
                return android.net.Uri.fromFile(dest).toString()
            }

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val namesArr = o.optJSONArray("names") ?: org.json.JSONArray()
                val names = buildList {
                    val primary = o.optString("name", "").trim()
                    if (primary.isNotBlank()) add(primary)
                    for (j in 0 until namesArr.length()) namesArr.optString(j).trim().takeIf(String::isNotBlank)?.let { if (none { x -> normText(x) == normText(it) }) add(it) }
                }
                val phonesArr = o.optJSONArray("phones") ?: org.json.JSONArray()
                val importedPhones = buildList {
                    for (j in 0 until phonesArr.length()) {
                        val po = phonesArr.optJSONObject(j) ?: continue
                        val num = po.optString("number", "").trim()
                        if (num.isNotBlank()) add(Triple(po, num, po.optBoolean("primary", false)))
                    }
                }
                if (importedPhones.isEmpty()) continue
                val phoneKeys = importedPhones.map { normPhone(it.second) }.filter(String::isNotBlank).toSet()
                val idx = merged.indexOfFirst { c ->
                    phoneKeys.contains(normPhone(c.phone)) || c.extraPhones.any { phoneKeys.contains(normPhone(it.number)) }
                }

                val addrArr = o.optJSONArray("addresses") ?: org.json.JSONArray()
                data class InAddr(val address: String, val lat: String, val lng: String, val primary: Boolean, val photo: String)
                val importedAddresses = buildList {
                    for (j in 0 until addrArr.length()) {
                        val a = addrArr.optJSONObject(j) ?: continue
                        val ad = a.optString("address", "").trim()
                        if (ad.isBlank()) continue
                        val rawLat = if (a.isNull("lat")) "" else a.optString("lat", "").trim()
                        val rawLng = if (a.isNull("lng")) "" else a.optString("lng", "").trim()
                        val (lat, lng) = CustomerCoordinateSafety.fillEmptyPair("", "", rawLat, rawLng)
                        add(InAddr(ad, lat, lng, a.optBoolean("primary", false), importPhoto(a.optString("housePhoto", ""))))
                    }
                }
                val note = o.optString("note", "").trim()

                if (idx < 0) {
                    val primaryPhoneRec = importedPhones.firstOrNull { it.third } ?: importedPhones.first()
                    val pm = methods(primaryPhoneRec.first)
                    val primaryAddr = importedAddresses.firstOrNull { it.primary } ?: importedAddresses.firstOrNull()
                    val primaryName = names.firstOrNull().orEmpty().ifBlank { primaryPhoneRec.second }
                    val extrasPhones = importedPhones.filter { it !== primaryPhoneRec }.map { rec ->
                        val m = methods(rec.first); CustomerPhone(rec.second, if (m.first) "Gọi" else if (m.second) "Zalo" else "SMS", m.first, m.second, m.third)
                    }
                    val extrasAddr = importedAddresses.filter { it !== primaryAddr }.map { a -> CustomerAddress(a.address, a.lat, a.lng, false, a.photo) }
                    merged.add(Customer(
                        id = nextId++, name = primaryName, phone = primaryPhoneRec.second,
                        address = primaryAddr?.address.orEmpty(), latitude = primaryAddr?.lat.orEmpty(), longitude = primaryAddr?.lng.orEmpty(),
                        initials = createInitials(primaryName), aliases = names.drop(1), extraPhones = extrasPhones, extraAddresses = extrasAddr,
                        note = note, primaryCanCall = pm.first, primaryCanZalo = pm.second, primaryCanSms = pm.third,
                        photoUri = primaryAddr?.photo.orEmpty()
                    ))
                    added++
                } else {
                    var c = merged[idx]
                    var changed = false
                    val aliases = c.aliases.toMutableList()
                    names.forEach { n ->
                        if (normText(n) != normText(c.name) && aliases.none { normText(it) == normText(n) }) { aliases.add(n); changed = true }
                    }

                    var primaryCall = c.primaryCanCall
                    var primaryZalo = c.primaryCanZalo
                    var primarySms = c.primaryCanSms
                    val extrasPhones = c.extraPhones.toMutableList()
                    importedPhones.forEach { rec ->
                        val key = normPhone(rec.second)
                        val m = methods(rec.first)
                        if (key == normPhone(c.phone)) {
                            val nc = primaryCall || m.first; val nz = primaryZalo || m.second; val ns = primarySms || m.third
                            if (nc != primaryCall || nz != primaryZalo || ns != primarySms) { primaryCall = nc; primaryZalo = nz; primarySms = ns; changed = true }
                        } else {
                            val pi = extrasPhones.indexOfFirst { normPhone(it.number) == key }
                            if (pi < 0 && key.isNotBlank()) {
                                extrasPhones.add(CustomerPhone(rec.second, if (m.first) "Gọi" else if (m.second) "Zalo" else "SMS", m.first, m.second, m.third)); changed = true
                            } else if (pi >= 0) {
                                val old = extrasPhones[pi]
                                val nw = old.copy(canCall = old.canCall || m.first, canZalo = old.canZalo || m.second, canSms = old.canSms || m.third)
                                if (nw != old) { extrasPhones[pi] = nw; changed = true }
                            }
                        }
                    }

                    var mainAddress = c.address
                    var mainLat = c.latitude
                    var mainLng = c.longitude
                    var mainPhoto = c.photoUri
                    val extrasAddr = c.extraAddresses.toMutableList()
                    importedAddresses.forEach { a ->
                        if (normText(a.address) == normText(mainAddress) && mainAddress.isNotBlank()) {
                            val coordinates = CustomerCoordinateSafety.fillEmptyPair(mainLat, mainLng, a.lat, a.lng)
                            if (coordinates != (mainLat to mainLng)) {
                                mainLat = coordinates.first; mainLng = coordinates.second; changed = true
                            }
                            if (a.photo.isNotBlank() && a.photo != mainPhoto) { mainPhoto = a.photo; changed = true }
                        } else {
                            val ai = extrasAddr.indexOfFirst { normText(it.address) == normText(a.address) }
                            if (ai >= 0) {
                                val old = extrasAddr[ai]
                                val coordinates = CustomerCoordinateSafety.fillEmptyPair(old.latitude, old.longitude, a.lat, a.lng)
                                val nw = old.copy(
                                    latitude = coordinates.first,
                                    longitude = coordinates.second,
                                    photoUri = if (a.photo.isNotBlank()) a.photo else old.photoUri
                                )
                                if (nw != old) { extrasAddr[ai] = nw; changed = true }
                            } else if (mainAddress.isBlank()) {
                                mainAddress = a.address
                                val coordinates = CustomerCoordinateSafety.fillEmptyPair(mainLat, mainLng, a.lat, a.lng)
                                mainLat = coordinates.first; mainLng = coordinates.second; mainPhoto = a.photo; changed = true
                            } else {
                                extrasAddr.add(CustomerAddress(a.address, a.lat, a.lng, false, a.photo)); changed = true
                            }
                        }
                    }
                    var mergedNote = c.note
                    if (note.isNotBlank() && !mergedNote.contains(note, ignoreCase = true)) {
                        mergedNote = if (mergedNote.isBlank()) note else mergedNote + "\n" + note
                        changed = true
                    }
                    if (changed) {
                        c = c.copy(
                            aliases = aliases, extraPhones = extrasPhones, extraAddresses = extrasAddr,
                            address = mainAddress, latitude = mainLat, longitude = mainLng, photoUri = mainPhoto, note = mergedNote,
                            primaryCanCall = primaryCall, primaryCanZalo = primaryZalo, primaryCanSms = primarySms
                        )
                        merged[idx] = c
                        updated++
                    }
                }
            }
            tempRoot.deleteRecursively()
            CustomerBackupResult(arr.length(), added, updated, arr.length() - added - updated, imageCount, missingImages) to merged
        }
        check(CustomerCoordinateSafety.unchangedSinceImport(customerState.toList(), existing)) {
            "Dữ liệu khách đã thay đổi trong lúc nhập. Chưa ghi đè khách nào; hãy nhập lại ZIP."
        }
        customerState.clear(); customerState.addAll(pair.second); savePersistentData()
        return pair.first
    }

    suspend fun exportCustomerBackup(context: android.content.Context, uri: android.net.Uri): CustomerBackupResult {
        val snapshot = customerState.toList()
        return withContext(Dispatchers.IO) {
            data class PhotoJob(val source: String, val zipPath: String)
            val photoJobs = mutableListOf<PhotoJob>()
            var photoIndex = 0
            var missingImages = 0

            fun registerPhoto(source: String): String? {
                if (source.isBlank()) return null
                photoIndex++
                val path = "photos/customer_gate/customer_gate_${photoIndex}.webp"
                photoJobs.add(PhotoJob(source, path))
                return path
            }
            fun methods(call: Boolean, zalo: Boolean, sms: Boolean): org.json.JSONArray = org.json.JSONArray().apply {
                if (call) put("CALL"); if (sms) put("SMS"); if (zalo) put("ZALO")
            }
            fun phoneJson(number: String, primary: Boolean, call: Boolean, zalo: Boolean, sms: Boolean) = org.json.JSONObject().apply {
                put("number", number); put("primary", primary); put("methods", methods(call, zalo, sms))
            }
            fun addrJson(a: String, lat: String, lng: String, primary: Boolean, photo: String) = org.json.JSONObject().apply {
                put("address", a)
                lat.toDoubleOrNull()?.let { put("lat", it) }
                lng.toDoubleOrNull()?.let { put("lng", it) }
                put("primary", primary)
                registerPhoto(photo)?.let { put("housePhoto", it) }
            }

            val customersJson = org.json.JSONArray()
            snapshot.forEach { c ->
                customersJson.put(org.json.JSONObject().apply {
                    put("id", "ghp-${c.id}")
                    put("name", c.name)
                    put("names", org.json.JSONArray(listOf(c.name) + c.aliases))
                    put("note", c.note)
                    put("streetName", c.streetName)
                    put("phones", org.json.JSONArray().apply {
                        put(phoneJson(c.phone, true, c.primaryCanCall, c.primaryCanZalo, c.primaryCanSms))
                        c.extraPhones.forEach { p -> put(phoneJson(p.number, false, p.canCall, p.canZalo, p.canSms)) }
                    })
                    put("addresses", org.json.JSONArray().apply {
                        if (c.address.isNotBlank()) put(addrJson(c.address, c.latitude, c.longitude, true, c.photoUri))
                        c.extraAddresses.forEach { a -> put(addrJson(a.address, a.latitude, a.longitude, false, a.photoUri)) }
                    })
                })
            }

            val manifestEntries = org.json.JSONArray()
            fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            fun addManifest(path: String, bytes: ByteArray) {
                manifestEntries.put(org.json.JSONObject().apply { put("path", path); put("bytes", bytes.size); put("sha256", sha256(bytes)) })
            }
            fun readPhoto(source: String): ByteArray? = runCatching {
                val u = android.net.Uri.parse(source)
                when (u.scheme) {
                    "content" -> context.contentResolver.openInputStream(u)?.use { it.readBytes() }
                    "file" -> java.io.File(requireNotNull(u.path)).takeIf { it.exists() }?.readBytes()
                    else -> java.io.File(source).takeIf { it.exists() }?.readBytes()
                }
            }.getOrNull()
            fun toWebp(bytes: ByteArray): ByteArray? = runCatching {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                bmp.recycle()
                out.toByteArray()
            }.getOrNull()

            val customersBytes = customersJson.toString().toByteArray(Charsets.UTF_8)
            val evidenceBytes = "[]".toByteArray(Charsets.UTF_8)
            var writtenImages = 0
            context.contentResolver.openOutputStream(uri, "w")?.use { rawOut ->
                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(rawOut)).use { zout ->
                    fun writeEntry(path: String, bytes: ByteArray, manifest: Boolean = true) {
                        zout.putNextEntry(java.util.zip.ZipEntry(path)); zout.write(bytes); zout.closeEntry()
                        if (manifest) addManifest(path, bytes)
                    }
                    writeEntry("customers.json", customersBytes)
                    writeEntry("transfer_evidence.json", evidenceBytes)
                    photoJobs.forEach { job ->
                        val sourceBytes = readPhoto(job.source)
                        val webp = sourceBytes?.let(::toWebp)
                        if (webp != null) { writeEntry(job.zipPath, webp); writtenImages++ } else missingImages++
                    }
                    val manifest = org.json.JSONObject().apply {
                        put("format", "giaohang-customer-backup")
                        put("schemaVersion", 1)
                        put("createdAt", System.currentTimeMillis())
                        put("customers", snapshot.size)
                        put("transferEvidence", 0)
                        put("images", writtenImages)
                        put("missingImages", missingImages)
                        put("entries", manifestEntries)
                    }.toString().toByteArray(Charsets.UTF_8)
                    writeEntry("manifest.json", manifest, manifest = false)
                }
            } ?: error("Không tạo được file ZIP")
            CustomerBackupResult(snapshot.size, 0, 0, 0, writtenImages, missingImages)
        }
    }

}
