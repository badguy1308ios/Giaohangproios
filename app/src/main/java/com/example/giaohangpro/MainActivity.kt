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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.launch
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
    val shop: String = "",
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
enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM, SETTINGS, MONEY_LEDGER, VTMAN_EXPORT, CUSTOMER_BACKUP }

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
    var returnOrderCode by remember { mutableStateOf<String?>(null) }
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
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
            AppScreen.CUSTOMER_BACKUP -> screen = AppScreen.SETTINGS
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
        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.MAIN }, onMoneyLedger = { screen = AppScreen.MONEY_LEDGER }, onVtmanExport = { screen = AppScreen.VTMAN_EXPORT }, onCustomerBackup = { screen = AppScreen.CUSTOMER_BACKUP })
        AppScreen.MONEY_LEDGER -> MoneyLedgerScreen(onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.CUSTOMER_BACKUP -> CustomerBackupScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
        AppScreen.VTMAN_EXPORT -> VtmanExportScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
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
private fun SettingsScreen(onBack: () -> Unit, onMoneyLedger: () -> Unit, onVtmanExport: () -> Unit, onCustomerBackup: () -> Unit) {
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
                SettingsItem(Icons.Default.SwapHoriz, "IMPORT / EXPORT DỮ LIỆU", "Nhập / xuất khách hàng dạng ZIP, gồm tọa độ và ảnh cổng") { onCustomerBackup() }
                SettingsDivider()
                SettingsItem(Icons.Default.FileDownload, "VTMAN EXPORT", "Nạp MVĐ và lấy thông tin đơn trực tiếp từ VTMan") { onVtmanExport() }
            }
            SettingsSection("VẬN HÀNH") {
                SettingsItem(Icons.Default.Inventory2, "XỬ LÝ ĐƠN") { Toast.makeText(context,"Xử lý đơn",Toast.LENGTH_SHORT).show() }
                SettingsDivider()
                SettingsItem(Icons.Default.Route, "TUYẾN GIAO HÀNG") { showRouteSettings = true }
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

private fun csvCell(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""



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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            busy = true
            status = "Đang nhập dữ liệu khách hàng..."
            runCatching { vm.importCustomerBackup(context, uri) }
                .onSuccess { r ->
                    status = "Nhập xong: +${r.added} khách mới, cập nhật ${r.updated}, ${r.images} ảnh"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    status = "Không nhập được file: ${e.message ?: "lỗi không xác định"}"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
            busy = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Đang xuất dữ liệu khách hàng..."
            runCatching { vm.exportCustomerBackup(context, uri) }
                .onSuccess { r ->
                    status = "Xuất xong ${r.total} khách, ${r.images} ảnh${if (r.missingImages > 0) ", thiếu ${r.missingImages} ảnh" else ""}"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    status = "Không xuất được file: ${e.message ?: "lỗi không xác định"}"
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        CustomerPageHeader("IMPORT / EXPORT DỮ LIỆU", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DỮ LIỆU KHÁCH HÀNG", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Định dạng ZIP tương thích cấu trúc: manifest.json + customers.json + photos/customer_gate/.", color = TextGray, fontSize = 12.sp)
                    Text("Đối chiếu theo SĐT. Khách đã có sẽ được gộp thêm tên phụ, SĐT phụ, địa chỉ, tọa độ và ảnh cổng mới; dữ liệu trống không ghi đè dữ liệu cũ.", color = TextGray, fontSize = 12.sp)
                }
            }

            Button(
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("NHẬP DỮ LIỆU KHÁCH HÀNG", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                    exportLauncher.launch("GiaoHangPro_KhachHang_${stamp}.zip")
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("XUẤT DỮ LIỆU KHÁCH HÀNG", fontWeight = FontWeight.Bold)
            }

            Card(
                Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OrangeLight)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(status, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Text("Quy tắc nhập", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("• SĐT trùng → cập nhật/gộp vào khách hiện có.\n• Tên hoặc biệt danh mới → thêm vào tên phụ.\n• SĐT mới → thêm SĐT phụ.\n• Địa chỉ mới → thêm địa chỉ phụ.\n• Địa chỉ đã có nhưng thiếu tọa độ → bổ sung tọa độ.\n• Ảnh cổng trong ZIP → lưu vào đúng địa chỉ tương ứng.\n• Không tự xóa dữ liệu đang có.", color = TextGray, fontSize = 12.sp)
        }
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

private fun uiNormPhone(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
        digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
        else -> digits
    }
}

private data class DeliveryGroup(val key: String, val customer: Customer?, val orders: List<Order>)

private fun deliveryGroupPoint(group: DeliveryGroup): MapPoint? {
    group.orders.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    val c = group.customer ?: return null
    pointFromStrings(c.latitude, c.longitude)?.let { return it }
    c.extraAddresses.firstNotNullOfOrNull { pointFromStrings(it.latitude, it.longitude) }?.let { return it }
    return null
}

private fun deliveryGroupHasCoordinate(group: DeliveryGroup): Boolean = deliveryGroupPoint(group) != null

private fun straightDistanceMeters(a: MapPoint, b: MapPoint): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(a.latitude)
    val p2 = Math.toRadians(b.latitude)
    val dp = Math.toRadians(b.latitude - a.latitude)
    val dl = Math.toRadians(b.longitude - a.longitude)
    val h = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) + kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
}

private fun buildDeliveryGroups(orders: List<Order>, customers: List<Customer>): List<DeliveryGroup> {
    val customerByPhone = mutableMapOf<String, Customer>()
    customers.forEach { c ->
        (listOf(c.phone) + c.extraPhones.map { it.number }).forEach { raw ->
            uiNormPhone(raw).takeIf(String::isNotBlank)?.let { customerByPhone[it] = c }
        }
    }
    val grouped = linkedMapOf<String, MutableList<Order>>()
    val customerForKey = mutableMapOf<String, Customer?>()
    orders.forEach { order ->
        val normalizedPhone = uiNormPhone(order.phone)
        val customer = customerByPhone[normalizedPhone]
        val key = when {
            customer != null -> "C:${customer.id}"
            normalizedPhone.isNotBlank() -> "P:$normalizedPhone"
            else -> "O:${order.code}"
        }
        grouped.getOrPut(key) { mutableListOf() }.add(order)
        customerForKey[key] = customer
    }
    return grouped.map { (key, list) -> DeliveryGroup(key, customerForKey[key], list) }
}

private fun orderMoneyValue(raw: String): Long = raw.filter(Char::isDigit).toLongOrNull() ?: 0L
private fun groupMoneyText(group: DeliveryGroup): String = fmtMoney(group.orders.sumOf { orderMoneyValue(it.amount) }) + "đ"
private fun groupRepresentative(group: DeliveryGroup): Order {
    val first = group.orders.first()
    val c = group.customer
    return first.copy(
        customer = c?.name ?: first.customer,
        phone = c?.phone ?: first.phone,
        address = c?.address?.takeIf(String::isNotBlank) ?: first.address,
        latitude = c?.latitude?.takeIf(String::isNotBlank) ?: first.latitude,
        longitude = c?.longitude?.takeIf(String::isNotBlank) ?: first.longitude,
        amount = groupMoneyText(group),
        item = if (group.orders.size > 1) "${group.orders.size} MVĐ" else first.item
    )
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
fun MapScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenOrder: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activeGroups = buildDeliveryGroups(vm.orders.filterNot { it.status.equals("Đã giao", true) }, vm.customers)
    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember(activeGroups.map { it.key }) { mutableStateOf(activeGroups.map { it.key }) }
    var editMarker by remember { mutableStateOf<MapOrderMarker?>(null) }
    var editNumberText by remember { mutableStateOf("") }
    var editOnMap by remember { mutableStateOf(false) }

    fun commitGroupedDirectStt() {
        val marker = editMarker ?: return
        val maxStt = draft.size.coerceAtLeast(1)
        val target = editNumberText.toIntOrNull()?.coerceIn(1, maxStt) ?: return
        val groupKey = activeGroups.firstOrNull { g -> g.orders.any { it.code == marker.order.code } }?.key ?: return
        val current = draft.indexOf(groupKey)
        if (current >= 0) {
            val next = draft.toMutableList()
            val moving = next.removeAt(current)
            next.add((target - 1).coerceIn(0, next.size), moving)
            draft = next
            val byKey = activeGroups.associateBy { it.key }
            vm.reorderOrders(next.mapNotNull(byKey::get).flatMap { it.orders.map(Order::code) })
        }
        editMarker = null
        editNumberText = ""
        editOnMap = false
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("STT,MVĐ\n")
                activeGroups.forEachIndexed { i, g -> append("${i + 1},${csvCell(g.orders.joinToString(" ") { it.code })}\n") }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context, "Đã xuất ${activeGroups.size} điểm giao", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context, "Không xuất được STT", Toast.LENGTH_LONG).show() }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val codes = text.lineSequence().drop(1).mapNotNull { line -> line.substringAfter(',', "").trim().trim('"').split(' ').firstOrNull()?.takeIf(String::isNotBlank) }.toList()
            val byRep = activeGroups.associateBy { it.orders.first().code }
            val ordered = codes.mapNotNull { byRep[it] }.flatMap { it.orders.map(Order::code) }
            val remaining = activeGroups.filterNot { it.orders.first().code in codes }.flatMap { it.orders.map(Order::code) }
            vm.reorderOrders(ordered + remaining)
        }.onFailure { Toast.makeText(context, "Không đọc được file STT", Toast.LENGTH_LONG).show() }
    }

    val orderedGroups = if (editing) draft.mapNotNull { key -> activeGroups.firstOrNull { it.key == key } } else activeGroups
    val displayOrders = orderedGroups.map(::groupRepresentative)

    BaseMapScreen(
        orders = displayOrders,
        customers = vm.customers,
        anchorPoint = vm.routeAnchorPoint(),
        routeNumberingEnabled = vm.routeNumberingEnabled,
        editingStt = editing,
        focusOrderCode = focusOrderCode,
        onFocusConsumed = onFocusConsumed,
        onOpenOrder = onOpenOrder,
        onCreateRoute = {
            val located = activeGroups.filter { deliveryGroupPoint(it) != null }.toMutableList()
            val pending = activeGroups.filter { deliveryGroupPoint(it) == null }
            val sortedGroups = mutableListOf<DeliveryGroup>()
            var current = vm.routeAnchorPoint() ?: located.firstOrNull()?.let(::deliveryGroupPoint) ?: DEFAULT_MAP_POINT
            while (located.isNotEmpty()) {
                val next = located.minByOrNull { g ->
                    val p = deliveryGroupPoint(g) ?: return@minByOrNull Double.MAX_VALUE
                    straightDistanceMeters(current, p) - vm.learnedRouteWeight(current, p) * 60.0
                } ?: break
                sortedGroups += next
                current = deliveryGroupPoint(next) ?: current
                located.remove(next)
            }
            sortedGroups += pending
            vm.reorderOrders(sortedGroups.flatMap { it.orders.map(Order::code) })
            vm.enableRouteNumbering()
            Toast.makeText(context, "Đã tạo tuyến theo ${sortedGroups.size} điểm giao", Toast.LENGTH_SHORT).show()
        },
        onEditRoute = {
            draft = activeGroups.map { it.key }
            editing = true
            Toast.makeText(context, "Chạm STT để nhập số mới trực tiếp", Toast.LENGTH_SHORT).show()
        },
        onClearRoute = {
            vm.clearRouteNumbering()
            editing = false
            editMarker = null
            editNumberText = ""
            editOnMap = false
            Toast.makeText(context, "Đã xóa toàn bộ STT", Toast.LENGTH_SHORT).show()
        },
        onSaveRoute = {
            val learnedPoints = draft.mapNotNull { key -> activeGroups.firstOrNull { it.key == key }?.let(::deliveryGroupPoint) }
            vm.learnRoutePattern(learnedPoints)
            editMarker = null
            editNumberText = ""
            editOnMap = false
            editing = false
        },
        onEditStt = { marker, fromMap ->
            if (editing) {
                editMarker = marker
                editNumberText = marker.number.toString()
                editOnMap = fromMap
            }
        },
        editingCode = editMarker?.order?.code,
        editingNumberText = editNumberText,
        editingOnMap = editOnMap,
        onEditingNumberChange = { editNumberText = it.filter(Char::isDigit).take(4) },
        onCommitEdit = { commitGroupedDirectStt() },
        onCancelEdit = {
            editMarker = null
            editNumberText = ""
            editOnMap = false
        },
        onExportStt = { exportLauncher.launch("giaohangpro_thu_tu_mvd.csv") },
        onImportStt = { importLauncher.launch("text/*") }
    )


    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave=false },
        title = { Text("Lưu STT tuyến") },
        text = { Text("Lưu thứ tự ${draft.size} điểm giao?") },
        confirmButton = { TextButton(onClick={
            val groupByKey=activeGroups.associateBy{it.key}
            vm.reorderOrders(draft.mapNotNull(groupByKey::get).flatMap{it.orders.map(Order::code)})
            confirmSave=false; editing=false
        }){Text("LƯU")} },
        dismissButton = { TextButton(onClick={confirmSave=false}){Text("HỦY")} }
    )
}

@Composable
private fun BaseMapScreen(
    orders: List<Order>,
    customers: List<Customer>,
    anchorPoint: MapPoint?,
    routeNumberingEnabled: Boolean,
    editingStt: Boolean,
    focusOrderCode: String?,
    onFocusConsumed: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onEditStt: (MapOrderMarker, Boolean) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    editingOnMap: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    val context = LocalContext.current
    val driverLocation by rememberDriverLocation()
    var selectedOrderCode by remember { mutableStateOf<String?>(null) }
    var mapExpanded by remember { mutableStateOf(false) }

    val mappedOrders = remember(orders, customers, driverLocation, anchorPoint, routeNumberingEnabled) {
        orders.mapIndexed { index, order ->
            val orderPoint = pointFromStrings(order.latitude, order.longitude)
            val customerPoint = customers.firstOrNull {
                it.phone.filter(Char::isDigit) == order.phone.filter(Char::isDigit) ||
                    it.name.equals(order.customer, ignoreCase = true) ||
                    it.address.equals(order.address, ignoreCase = true)
            }?.let { pointFromStrings(it.latitude, it.longitude) }
            val realPoint = orderPoint ?: customerPoint
            val displayPoint = realPoint ?: anchorPoint ?: driverLocation ?: DEFAULT_MAP_POINT
            MapOrderMarker(order, displayPoint, index + 1, realPoint != null, routeNumberingEnabled && realPoint != null)
        }
    }
    val selectedMarker = mappedOrders.firstOrNull { it.order.code == selectedOrderCode }

    LaunchedEffect(focusOrderCode, mappedOrders) {
        val code = focusOrderCode ?: return@LaunchedEffect
        if (mappedOrders.any { it.order.code == code }) selectedOrderCode = code
        onFocusConsumed()
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GoongOrderMap(
                modifier = Modifier.fillMaxSize(),
                orders = mappedOrders,
                driverLocation = driverLocation,
                selectedOrderNumber = selectedMarker?.number,
                expanded = mapExpanded,
                editingStt = editingStt,
                onToggleExpand = { mapExpanded = !mapExpanded },
                onOrderSelected = { selectedOrderCode = it.order.code },
                onEditStt = { marker -> onEditStt(marker, true) },
                editingCode = editingCode,
                editingNumberText = editingNumberText,
                showInlineEditor = editingStt && editingOnMap,
                onEditingNumberChange = onEditingNumberChange,
                onCommitEdit = onCommitEdit,
                onCancelEdit = onCancelEdit
            )

            if (!mapExpanded) {
                MapOrderBottomSheet(
                    orders = mappedOrders,
                    selectedNumber = selectedMarker?.number,
                    editingStt = editingStt,
                    onOrderClick = { marker -> if (!editingStt) onOpenOrder(marker.order.code) },
                    onNumberClick = { marker ->
                        selectedOrderCode = marker.order.code
                        if (editingStt) onEditStt(marker, false)
                    },
                    editingCode = editingCode,
                    editingNumberText = editingNumberText,
                    editingOnMap = editingOnMap,
                    onEditingNumberChange = onEditingNumberChange,
                    onCommitEdit = onCommitEdit,
                    onCancelEdit = onCancelEdit,
                    onNavigate = { marker ->
                        if (marker.hasRealCoordinate) {
                            openGoogleNavigation(context, marker.point)
                        } else {
                            Toast.makeText(context, "Đơn này chưa có tọa độ. Hãy bổ sung trong Khách hàng.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCreateRoute = onCreateRoute,
                    onEditRoute = onEditRoute,
                    onClearRoute = onClearRoute,
                    onSaveRoute = onSaveRoute,
                    onExportStt = onExportStt,
                    onImportStt = onImportStt
                )
            }
        }
    }
}

// Bottom sheet dạng danh sách đơn hàng giống hình tham chiếu: số thứ tự, nút dẫn đường, mã đơn, tên khách và số tiền.
@Composable
private fun BoxScope.MapOrderBottomSheet(
    orders: List<MapOrderMarker>,
    selectedNumber: Int?,
    editingStt: Boolean,
    onOrderClick: (MapOrderMarker) -> Unit,
    onNumberClick: (MapOrderMarker) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    editingOnMap: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onNavigate: (MapOrderMarker) -> Unit,
    onCreateRoute: () -> Unit,
    onEditRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onExportStt: () -> Unit,
    onImportStt: () -> Unit
) {
    var routeMenuExpanded by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedNumber, orders) {
        val n = selectedNumber ?: return@LaunchedEffect
        val i = orders.indexOfFirst { it.number == n }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        color = Color.White.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 185.dp, max = 250.dp).padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(3.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xFF9BA8B8))
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, null, tint = Orange, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (editingStt) "Sửa STT • ${orders.size} đơn" else "${orders.size} đơn hàng",
                    color = Navy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { if (editingStt) onSaveRoute() else routeMenuExpanded = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (editingStt) Icons.Default.Save else Icons.Default.KeyboardArrowUp,
                            if (editingStt) "Lưu STT" else "Công cụ tuyến",
                            tint = Orange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    if (!editingStt) {
                        DropdownMenu(expanded = routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Tạo tuyến") },
                                leadingIcon = { Icon(Icons.Default.Route, null) },
                                onClick = { routeMenuExpanded = false; onCreateRoute() }
                            )
                            DropdownMenuItem(
                                text = { Text("Sửa STT") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { routeMenuExpanded = false; onEditRoute() }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa STT") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = { routeMenuExpanded = false; onClearRoute() }
                            )
                            DropdownMenuItem(
                                text = { Text("Xuất STT") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = { routeMenuExpanded = false; onExportStt() }
                            )
                            DropdownMenuItem(
                                text = { Text("Nhập STT") },
                                leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                                onClick = { routeMenuExpanded = false; onImportStt() }
                            )
                        }
                    }
                }
            }
            if (editingStt) {
                Text("Chạm STT trong list hoặc bong bóng trên bản đồ rồi gõ số mới trực tiếp.", color = TextGray, fontSize = 10.sp)
            }
            Spacer(Modifier.height(if (editingStt) 4.dp else 10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(orders, key = { it.order.code }) { marker ->
                    MapOrderListRow(
                        marker = marker,
                        selected = marker.number == selectedNumber,
                        editingStt = editingStt,
                        editingCode = editingCode,
                        editingNumberText = editingNumberText,
                        inlineEditingHere = !editingOnMap,
                        onEditingNumberChange = onEditingNumberChange,
                        onCommitEdit = onCommitEdit,
                        onCancelEdit = onCancelEdit,
                        onClick = { onOrderClick(marker) },
                        onNumberClick = { onNumberClick(marker) },
                        onNavigate = { onNavigate(marker) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapOrderListRow(
    marker: MapOrderMarker,
    selected: Boolean,
    editingStt: Boolean,
    editingCode: String?,
    editingNumberText: String,
    inlineEditingHere: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onClick: () -> Unit,
    onNumberClick: () -> Unit,
    onNavigate: () -> Unit
) {
    val order = marker.order
    Card(
        modifier = Modifier.fillMaxWidth().height(62.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) OrangeLight else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingStt && inlineEditingHere && editingCode == order.code) {
                val focusRequester = remember(order.code) { androidx.compose.ui.focus.FocusRequester() }
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                LaunchedEffect(editingCode) {
                    kotlinx.coroutines.delay(80)
                    focusRequester.requestFocus()
                }
                OutlinedTextField(
                    value = editingNumberText,
                    onValueChange = onEditingNumberChange,
                    modifier = Modifier.width(58.dp).height(52.dp).focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        onCommitEdit()
                        focusManager.clearFocus()
                    }),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
            } else {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onNumberClick() },
                    contentAlignment = Alignment.Center
                ) {
                    RouteStateCircle(if (marker.showNumber) marker.number else null, marker.hasRealCoordinate, selected || editingStt)
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (marker.hasRealCoordinate) Blue else Color(0xFFB6C0CC))
                    .clickable(enabled = marker.hasRealCoordinate) { onNavigate() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Navigation, "Dẫn đường", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(order.code, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(order.customer, color = TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!marker.hasRealCoordinate) Text("Chưa có tọa độ", color = OrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(order.amount, color = MoneyGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Default.ChevronRight, "Xem đơn", tint = TextGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Tạo Drawable marker kiểu bong bóng bằng Canvas Android để OSMDroid có thể hiển thị số thứ tự ở giữa.
private fun createNumberBubbleDrawable(
    context: android.content.Context,
    number: Int,
    isPending: Boolean,
    showNumber: Boolean = true,
    selected: Boolean = false
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density // Quy đổi dp sang pixel.
    val width = (34 * density).toInt() // Chiều rộng bong bóng.
    val height = (84 * density).toInt() // Nửa dưới trong suốt để tâm bitmap trùng đầu mũi nhọn.
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888) // Tạo vùng ảnh trong suốt.
    val canvas = android.graphics.Canvas(bitmap) // Canvas Android để tự vẽ marker.
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) // Bật khử răng cưa cho hình tròn/chữ.
    val bubbleColor = when {
        selected -> android.graphics.Color.rgb(22, 141, 226)
        isPending -> android.graphics.Color.rgb(117, 132, 153)
        else -> android.graphics.Color.rgb(227, 75, 10)
    }
    paint.color = bubbleColor // Áp dụng màu nền bong bóng.

    val bodyBottom = (32 * density) // Đáy phần thân trước mũi nhọn.
    val radius = (12 * density) // Bán kính bo góc.
    val rect = android.graphics.RectF(0f, 0f, width.toFloat(), bodyBottom) // Khung phần thân bong bóng.
    canvas.drawRoundRect(rect, radius, radius, paint) // Vẽ thân bo tròn.

    val pointer = android.graphics.Path().apply { // Tạo mũi nhọn chỉ xuống vị trí tọa độ.
        moveTo(width / 2f - 6 * density, bodyBottom)
        lineTo(width / 2f, 42 * density)
        lineTo(width / 2f + 6 * density, bodyBottom)
        close()
    }
    canvas.drawPath(pointer, paint) // Vẽ mũi nhọn cùng màu thân.

    paint.color = android.graphics.Color.WHITE // Số thứ tự màu trắng để dễ đọc.
    paint.textSize = 14 * density // Kích thước chữ.
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD // Chữ đậm.
    paint.textAlign = android.graphics.Paint.Align.CENTER // Căn giữa theo chiều ngang.
    val textY = bodyBottom / 2f - (paint.ascent() + paint.descent()) / 2f // Căn giữa theo chiều dọc.
    if (showNumber) canvas.drawText(number.toString(), width / 2f, textY, paint)

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
    val hasRealCoordinate: Boolean,
    val showNumber: Boolean = true
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
    editingStt: Boolean,
    onToggleExpand: () -> Unit,
    onOrderSelected: (MapOrderMarker) -> Unit,
    onEditStt: (MapOrderMarker) -> Unit,
    editingCode: String?,
    editingNumberText: String,
    showInlineEditor: Boolean,
    onEditingNumberChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialDriverFocus by remember { mutableStateOf(false) }
    var inlineEditorOffset by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
    val inlineEditorMarker = orders.firstOrNull { it.order.code == editingCode }

    LaunchedEffect(map, inlineEditorMarker, showInlineEditor) {
        if (!showInlineEditor) return@LaunchedEffect
        val readyMap = map ?: return@LaunchedEffect
        val marker = inlineEditorMarker ?: return@LaunchedEffect
        // Chờ camera focus marker xong rồi đặt textbox ngay trên bong bóng.
        kotlinx.coroutines.delay(260)
        val screenPoint = readyMap.projection.toScreenLocation(LatLng(marker.point.latitude, marker.point.longitude))
        val density = context.resources.displayMetrics.density
        val editorWidth = (64f * density).toInt()
        val x = (screenPoint.x - editorWidth / 2f).toInt().coerceIn(0, (mapView.width - editorWidth).coerceAtLeast(0))
        val y = (screenPoint.y - 64f * density).toInt().coerceAtLeast(0)
        inlineEditorOffset = androidx.compose.ui.unit.IntOffset(x, y)
    }

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
                                val marker = orders.firstOrNull { it.number == number }
                                if (marker != null) {
                                    onOrderSelected(marker)
                                    if (editingStt) onEditStt(marker)
                                    true
                                } else false
                            }
                        }
                    }
                }
            },
            update = { view -> view.getMapAsync { readyMap -> if (readyMap.style != null) map = readyMap } }
        )

        if (editingStt && showInlineEditor && inlineEditorMarker != null) {
            val focusRequester = remember(inlineEditorMarker.order.code) { androidx.compose.ui.focus.FocusRequester() }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            LaunchedEffect(editingCode) {
                kotlinx.coroutines.delay(120)
                focusRequester.requestFocus()
            }
            Surface(
                modifier = Modifier.offset { inlineEditorOffset }.width(64.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, Orange)
            ) {
                OutlinedTextField(
                    value = editingNumberText,
                    onValueChange = onEditingNumberChange,
                    modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        onCommitEdit()
                        focusManager.clearFocus()
                    }),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapControlButton(Icons.Default.MyLocation, "Vị trí của tôi") {
                driverLocation?.let { point -> map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0)) }
            }
            MapControlButton(Icons.Default.Add, "Phóng to") { map?.animateCamera(CameraUpdateFactory.zoomBy(1.0)) }
            MapControlButton(Icons.Default.Remove, "Thu nhỏ") { map?.animateCamera(CameraUpdateFactory.zoomBy(-1.0)) }
            MapControlButton(if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Mở rộng bản đồ") { onToggleExpand() }
        }
    }

    LaunchedEffect(map, driverLocation) {
        val readyMap = map
        val point = driverLocation
        if (!didInitialDriverFocus && readyMap != null && point != null) {
            readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
            didInitialDriverFocus = true
        }
    }

    LaunchedEffect(map, orders, driverLocation, selectedOrderNumber, editingStt) {
        map?.let { readyMap ->
            readyMap.clear()
            driverLocation?.let { point ->
                val driverIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(createDriverMotorbikeBitmap(context))
                readyMap.addMarker(
                    MarkerOptions().position(LatLng(point.latitude, point.longitude)).icon(driverIcon)
                        .title("🛵 Vị trí hiện tại của tài xế").snippet("GPS đang cập nhật")
                )
            }

            // Vẽ marker đang chọn cuối cùng để luôn nằm trên các bong bóng gần kề.
            val drawOrders = if (selectedOrderNumber == null) orders else orders.sortedBy { it.number == selectedOrderNumber }
            drawOrders.forEach { markerData ->
                val order = markerData.order
                val numberBitmap = (createNumberBubbleDrawable(context, markerData.number, !markerData.hasRealCoordinate, markerData.showNumber, markerData.number == selectedOrderNumber) as android.graphics.drawable.BitmapDrawable).bitmap
                val numberIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(numberBitmap)
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(markerData.point.latitude, markerData.point.longitude))
                        .icon(numberIcon)
                        .title("Đơn #${markerData.number} • ${order.code}")
                        .snippet(if (editingStt) "Chạm để đổi STT" else if (markerData.hasRealCoordinate) order.address else "Chưa có tọa độ giao hàng")
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
    focusUserLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSavePoint: (MapPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driverLocation by rememberDriverLocation()

    // Hai quy tắc độc lập, không được chồng lên nhau:
    // - Có tọa độ thật của khách: focus tọa độ khách đúng 1 lần khi mở.
    // - Chưa có tọa độ thật: lấy GPS người dùng đúng 1 lần đầu khi GPS sẵn sàng.
    // Sau lần focus đầu tiên, các bản tin GPS tiếp theo chỉ là dữ liệu vị trí,
    // tuyệt đối không tự kéo camera và không ghi đè điểm khách đã chọn.
    var selectedPoint by remember(initialPoint) { mutableStateOf(initialPoint) }
    var initialFocusDone by remember(initialPoint, focusUserLocation) {
        mutableStateOf(!focusUserLocation && initialPoint == null)
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

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

    // Chỉ chạy cho trường hợp khách CHƯA có tọa độ và GPS chưa sẵn sàng lúc style vừa mở.
    LaunchedEffect(driverLocation, map, styleReady, initialPoint, focusUserLocation, initialFocusDone) {
        if (initialPoint == null && focusUserLocation && !initialFocusDone) {
            val gps = driverLocation ?: return@LaunchedEffect
            val readyMap = map ?: return@LaunchedEffect
            if (!styleReady) return@LaunchedEffect

            selectedPoint = gps
            readyMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(gps.latitude, gps.longitude),
                    16.0
                )
            )
            readyMap.clear()
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(gps.latitude, gps.longitude))
                    .title("Vị trí đang chọn")
            )
            initialFocusDone = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Dialog tạo cửa sổ riêng nên phải tự chừa vùng status/navigation bar.
        // Quy tắc này giữ mọi nút trong màn hình picker nằm giữa thanh thông báo và thanh điều hướng.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = Background
        ) {
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
                                        styleReady = true

                                        when {
                                            initialPoint != null -> {
                                                // Mệnh đề 1: tọa độ thật của khách luôn thắng GPS.
                                                selectedPoint = initialPoint
                                                readyMap.cameraPosition = CameraPosition.Builder()
                                                    .target(LatLng(initialPoint.latitude, initialPoint.longitude))
                                                    .zoom(16.0)
                                                    .build()
                                                initialFocusDone = true
                                            }
                                            focusUserLocation && driverLocation != null -> {
                                                // Mệnh đề 2: nếu GPS đã có ngay lúc mở, dùng đúng lần này rồi khóa auto-focus.
                                                val gps = driverLocation!!
                                                selectedPoint = gps
                                                readyMap.cameraPosition = CameraPosition.Builder()
                                                    .target(LatLng(gps.latitude, gps.longitude))
                                                    .zoom(16.0)
                                                    .build()
                                                initialFocusDone = true
                                            }
                                            else -> {
                                                // Chờ GPS đầu tiên; không tự coi điểm mặc định là tọa độ khách.
                                                readyMap.cameraPosition = CameraPosition.Builder()
                                                    .target(LatLng(DEFAULT_MAP_POINT.latitude, DEFAULT_MAP_POINT.longitude))
                                                    .zoom(15.0)
                                                    .build()
                                            }
                                        }

                                        selectedPoint?.let { point ->
                                            readyMap.clear()
                                            readyMap.addMarker(
                                                MarkerOptions()
                                                    .position(LatLng(point.latitude, point.longitude))
                                                    .title("Vị trí đang chọn")
                                            )
                                        }

                                        readyMap.addOnMapClickListener { tapped ->
                                            selectedPoint = MapPoint(tapped.latitude, tapped.longitude)
                                            readyMap.clear()
                                            readyMap.addMarker(
                                                MarkerOptions()
                                                    .position(tapped)
                                                    .title("Vị trí đang chọn")
                                            )
                                            true
                                        }
                                    }
                                }
                            }
                        },
                        update = { view ->
                            view.getMapAsync { readyMap ->
                                if (readyMap.style != null) map = readyMap
                            }
                        }
                    )

                    selectedPoint?.let { point ->
                        Card(
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                        ) {
                            Text(
                                "Vĩ độ: %.6f\nKinh độ: %.6f".format(
                                    java.util.Locale.US,
                                    point.latitude,
                                    point.longitude
                                ),
                                modifier = Modifier.padding(12.dp),
                                color = Navy
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("HỦY", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { selectedPoint?.let(onSavePoint) },
                        enabled = selectedPoint != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Text("LƯU", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ================================================================
// 6. TAB CHI TIẾT ĐƠN
// ================================================================
@Composable
fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onNumberClick: (Order) -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val context = LocalContext.current
    val allGroups = buildDeliveryGroups(vm.orders, vm.customers)
    val activeGroups = allGroups.filterNot { g -> g.orders.all { it.status.equals("Đã giao", true) } }
    val deliveredGroups = allGroups.filter { g -> g.orders.all { it.status.equals("Đã giao", true) } }
    var keyword by remember { mutableStateOf("") }
    var showTools by remember { mutableStateOf(false) }
    var editPicker by remember { mutableStateOf(false) }
    var editOrder by remember { mutableStateOf<Order?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val q = keyword.trim()

    fun matches(g: DeliveryGroup): Boolean = q.isBlank() ||
        g.orders.any { o ->
            o.code.contains(q, true) || o.customer.contains(q, true) ||
                o.phone.contains(q, true) || o.address.contains(q, true) ||
                o.shop.contains(q, true) || o.item.contains(q, true)
        } || (g.customer?.name?.contains(q, true) == true)

    val visibleGroups = activeGroups.filter(::matches) + deliveredGroups.filter(::matches)
    val filteredOrders = if (q.isBlank()) vm.orders else vm.orders.filter { o ->
        o.code.contains(q, true) || o.customer.contains(q, true) || o.phone.contains(q, true) ||
            o.address.contains(q, true) || o.shop.contains(q, true) || o.item.contains(q, true)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword = it }
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Spacer(Modifier.height(6.dp))
            SearchBox(keyword, { keyword = it }, { keyword = "" }) {
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
                        onCheckedChange = { all -> selected = if (all) filteredOrders.map { it.code }.toSet() else emptySet() }
                    )
                    Text("Chọn tất cả", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                    TextButton(onClick = { deleteMode = false; selected = emptySet() }) { Text("HỦY") }
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
                        val delivered = group.orders.all { it.status.equals("Đã giao", true) }
                        val routeStt = if (delivered || !vm.routeNumberingEnabled || !deliveryGroupHasCoordinate(group)) null else activeGroups.indexOfFirst { it.key == group.key }.takeIf { it >= 0 }?.plus(1)
                        DeliveryGroupCard(
                            routeStt = routeStt,
                            group = group,
                            delivered = delivered,
                            onNumberClick = { group.orders.firstOrNull()?.let(onNumberClick) },
                            onCustomerClick = { group.orders.firstOrNull()?.let(onCustomerClick) },
                            onDelivered = { group.orders.firstOrNull()?.let { vm.markDeliveredGroup(it.code) } }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showTools = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(44.dp),
            containerColor = Orange
        ) { Icon(Icons.Default.Edit, "Công cụ đơn", tint = Color.White) }

        DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }, modifier = Modifier.align(Alignment.BottomStart)) {
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
                onClick = { showTools = false; selected = emptySet(); deleteMode = true }
            )
        }
    }

    if (editPicker) {
        val editQuery = keyword.trim()
        val editCandidates = vm.orders.filter { o ->
            editQuery.isBlank() || o.code.contains(editQuery, true) || o.customer.contains(editQuery, true) ||
                o.phone.contains(editQuery, true) || o.address.contains(editQuery, true) ||
                o.shop.contains(editQuery, true) || o.item.contains(editQuery, true)
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
                vm.deleteOrders(selected)
                selected = emptySet()
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
    onDelivered: () -> Unit
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
                    if (delivered) {
                        Icon(Icons.Default.CheckCircle, "Đã giao", tint = Color(0xFF2E7D32), modifier = Modifier.size(38.dp))
                    } else {
                        Box(Modifier.size(38.dp).clip(CircleShape).clickable { onNumberClick() }, contentAlignment = Alignment.Center) {
                            RouteStateCircle(routeStt, deliveryGroupHasCoordinate(group), false)
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Text("${group.orders.size} đơn", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  •  Tổng COD ", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(groupMoneyText(group), color = MoneyGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }

                // Cụm nút thao tác dùng chung cho tất cả MVĐ trong Customer ID này.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, onClick = onDelivered)
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
            onCustomerClick = onCustomerClick,
            onDelivered = onDelivered,
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
            CopyableWaybillCode(order.code, Modifier.weight(1f))
            Text(order.status.ifBlank { if (delivered) "Đã giao" else "—" }, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(order.amount, color = MoneyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
        OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
        OrderInfoRow(Icons.Default.LocationOn, order.address)
        OrderInfoRow(Icons.Default.Inventory2, order.item)
        if (order.tags.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { order.tags.forEach { Tag(it) } }
        }
    }
}

@Composable
private fun SingleOrderDetailCard(
    order: Order,
    routeStt: Int?,
    hasRealCoordinate: Boolean,
    delivered: Boolean,
    onCustomerClick: () -> Unit,
    onDelivered: () -> Unit,
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
                if (!delivered) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { },
                        contentAlignment = Alignment.Center
                    ) { RouteStateCircle(routeStt, hasRealCoordinate, false) }
                    Spacer(Modifier.width(7.dp))
                }
                CopyableWaybillCode(order.code, Modifier.weight(1f))
                Text(order.status.ifBlank { if (delivered) "Đã giao" else "—" }, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(order.amount, color = MoneyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            OrderInfoRow(Icons.Default.Store, order.shop.ifBlank { order.customer })
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}", onClick = onCustomerClick)
            OrderInfoRow(Icons.Default.LocationOn, order.address)
            OrderInfoRow(Icons.Default.Inventory2, order.item)
            if (order.tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { order.tags.forEach { Tag(it) } }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionButton("Đã giao", Icons.Default.CheckCircle, filled = true, onClick = onDelivered)
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
        .padding(vertical = 1.dp)
    Row(
        rowModifier,
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
    filled: Boolean = false,
    onClick: () -> Unit = {}
) {
    Box(
        Modifier
            .height(36.dp)
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
            .clickable { onClick() }
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
private data class AddressDraft(val address: String, val latitude: String, val longitude: String, val isPrimary: Boolean, val photoUri: String = "")

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
                add(AddressDraft(customer.address, customer.latitude, customer.longitude, true, customer.photoUri))
                addAll(customer.extraAddresses.map { AddressDraft(it.address, it.latitude, it.longitude, false, it.photoUri) })
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
                    onSave(Customer(id = customer?.id ?: 0L, name = name.trim(), phone = primaryPhone.number.trim(), address = primaryAddress.address.trim(), latitude = primaryAddress.latitude.trim(), longitude = primaryAddress.longitude.trim(), initials = createInitials(name), aliases = names.drop(1).map { it.trim() }.filter { it.isNotBlank() }, extraPhones = validPhones.drop(1).map { CustomerPhone(it.number.trim(), if (it.canCall) "Gọi" else if (it.canZalo) "Zalo" else "SMS", it.canCall, it.canZalo, it.canSms) }, extraAddresses = validAddresses.filter { it !== primaryAddress }.map { CustomerAddress(it.address.trim(), it.latitude.trim(), it.longitude.trim(), false, it.photoUri) }, note = note.trim(), primaryCanCall = primaryPhone.canCall, primaryCanZalo = primaryPhone.canZalo, primaryCanSms = primaryPhone.canSms, photoUri = photoUri))
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
        placeholder={Text("Tìm mã đơn, tên người nhận...",color=TextGray,fontSize=14.sp,maxLines=1)},
        leadingIcon={Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Orange).clickable{onQrClick()},contentAlignment=Alignment.Center){Icon(Icons.Default.QrCodeScanner,"Quét QR",tint=Color.White,modifier=Modifier.size(23.dp))}},
        trailingIcon={if(value.isNotBlank()) IconButton(onClick=onClear){Icon(Icons.Default.Close,"Xóa",tint=TextGray,modifier=Modifier.size(20.dp))}},
        shape=RoundedCornerShape(14.dp),singleLine=true,
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Orange,unfocusedBorderColor=Border,focusedContainerColor=Color(0xFFF7F5F2),unfocusedContainerColor=Color(0xFFF7F5F2),cursorColor=OrangeDark))
}

@Composable
private fun RouteStateCircle(number: Int?, hasCoordinate: Boolean, selected: Boolean) {
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
class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE)
    var routeNumberingEnabled by mutableStateOf(prefs.getBoolean("route_numbering_enabled_v1", false))
        private set
    private var routeAnchorLat by mutableStateOf(prefs.getString("route_anchor_lat_v1", "").orEmpty())
    private var routeAnchorLng by mutableStateOf(prefs.getString("route_anchor_lng_v1", "").orEmpty())

    fun routeAnchorPoint(): MapPoint? {
        routeAnchorLat = prefs.getString("route_anchor_lat_v1", routeAnchorLat).orEmpty()
        routeAnchorLng = prefs.getString("route_anchor_lng_v1", routeAnchorLng).orEmpty()
        return pointFromStrings(routeAnchorLat, routeAnchorLng)
    }

    fun setRouteAnchor(point: MapPoint) {
        routeAnchorLat = "%.6f".format(java.util.Locale.US, point.latitude)
        routeAnchorLng = "%.6f".format(java.util.Locale.US, point.longitude)
        prefs.edit().putString("route_anchor_lat_v1", routeAnchorLat).putString("route_anchor_lng_v1", routeAnchorLng).apply()
    }

    fun enableRouteNumbering() {
        routeNumberingEnabled = true
        prefs.edit().putBoolean("route_numbering_enabled_v1", true).apply()
    }

    fun clearRouteNumbering() {
        routeNumberingEnabled = false
        prefs.edit().putBoolean("route_numbering_enabled_v1", false).apply()
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

    fun learnedRouteWeight(from: MapPoint, to: MapPoint): Double {
        val obj = runCatching { JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val scale = 500.0
        fun cell(p: MapPoint) = "${kotlin.math.floor(p.latitude * scale).toInt()},${kotlin.math.floor(p.longitude * scale).toInt()}"
        return obj.optDouble("${cell(from)}>${cell(to)}", 0.0)
    }

    private val orderState = mutableStateListOf<Order>()
    val orders: List<Order> get() = orderState
    private val customerState = mutableStateListOf<Customer>()
    val customers: List<Customer> get() = customerState

    init {
        val loaded = loadPersistentData()
        if (!loaded) {
            orderState.addAll(sampleOrders)
            customerState.addAll(sampleCustomers)
            savePersistentData()
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
                    shop=optString(o,"shop"), status=optString(o,"status").ifBlank { "Chưa giao" },
                    latitude=optString(o,"latitude"), longitude=optString(o,"longitude")
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
                    latitude=optString(o,"latitude"), longitude=optString(o,"longitude"), initials=optString(o,"initials"), aliases=aliases,
                    extraPhones=phones, extraAddresses=addresses, note=optString(o,"note"),
                    primaryCanCall=o.optBoolean("primaryCanCall",true), primaryCanZalo=o.optBoolean("primaryCanZalo",true),
                    primaryCanSms=o.optBoolean("primaryCanSms",true), photoUri=optString(o,"photoUri")
                ))
            }
        }
        true
    }.getOrElse { false }

    private fun savePersistentData() {
        val ordersArr=org.json.JSONArray()
        orderState.forEach { o ->
            ordersArr.put(org.json.JSONObject().apply {
                put("code",o.code); put("customer",o.customer); put("phone",o.phone); put("address",o.address); put("item",o.item)
                put("amount",o.amount); put("tags",org.json.JSONArray(o.tags)); put("shop",o.shop); put("status",o.status)
                put("latitude",o.latitude); put("longitude",o.longitude)
            })
        }
        val customersArr=org.json.JSONArray()
        customerState.forEach { c ->
            customersArr.put(org.json.JSONObject().apply {
                put("id",c.id); put("name",c.name); put("phone",c.phone); put("address",c.address); put("latitude",c.latitude); put("longitude",c.longitude)
                put("initials",c.initials); put("aliases",org.json.JSONArray(c.aliases)); put("note",c.note)
                put("primaryCanCall",c.primaryCanCall); put("primaryCanZalo",c.primaryCanZalo); put("primaryCanSms",c.primaryCanSms); put("photoUri",c.photoUri)
                put("extraPhones",org.json.JSONArray().apply { c.extraPhones.forEach { p -> put(org.json.JSONObject().apply { put("number",p.number); put("action",p.action); put("canCall",p.canCall); put("canZalo",p.canZalo); put("canSms",p.canSms) }) } })
                put("extraAddresses",org.json.JSONArray().apply { c.extraAddresses.forEach { a -> put(org.json.JSONObject().apply { put("address",a.address); put("latitude",a.latitude); put("longitude",a.longitude); put("isPrimary",a.isPrimary); put("photoUri",a.photoUri) }) } })
            })
        }
        prefs.edit().putString("orders",ordersArr.toString()).putString("customers",customersArr.toString()).apply()
    }

    fun findCustomer(id: Long): Customer? = customerState.firstOrNull { it.id == id }

    private fun normalizeCustomerPhone(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.startsWith("0084") && digits.length > 4 -> "0" + digits.drop(4)
            digits.startsWith("84") && digits.length >= 10 -> "0" + digits.drop(2)
            else -> digits
        }
    }

    fun findCustomerByPhone(phone: String): Customer? {
        val wanted = normalizeCustomerPhone(phone)
        if (wanted.isBlank()) return null
        return customerState.firstOrNull { customer ->
            normalizeCustomerPhone(customer.phone) == wanted || customer.extraPhones.any { normalizeCustomerPhone(it.number) == wanted }
        }
    }

    fun ensureCustomerFromImportedOrder(name: String, phone: String, address: String): Customer? {
        val normalized = normalizeCustomerPhone(phone)
        if (normalized.isBlank()) return null
        findCustomerByPhone(phone)?.let { return it }
        val cleanName = name.trim().ifBlank { phone.trim() }
        val newId = (customerState.maxOfOrNull { it.id } ?: 0L) + 1L
        val created = Customer(id=newId,name=cleanName,phone=phone.trim(),address=address.trim(),initials=createInitials(cleanName))
        customerState.add(created); savePersistentData(); return created
    }

    fun addCustomer(customer: Customer): Long {
        val newId=(customerState.maxOfOrNull { it.id } ?: 0L)+1L
        customerState.add(customer.copy(id=newId)); savePersistentData(); return newId
    }

    fun updateCustomer(updatedCustomer: Customer) {
        val index=customerState.indexOfFirst { it.id==updatedCustomer.id }
        if(index>=0){ customerState[index]=updatedCustomer; savePersistentData() }
    }

    fun deleteCustomer(id: Long) { if(customerState.removeAll { it.id==id }) savePersistentData() }

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
                    shop = g(ishop), status = g(ist).ifBlank { "Chưa giao" }
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

    fun updateOrder(updated: Order) {
        val i=orderState.indexOfFirst { it.code==updated.code }
        if(i>=0){ orderState[i]=updated; savePersistentData() }
    }

    fun deleteOrder(code: String) { if(orderState.removeAll { it.code==code }) savePersistentData() }

    fun markDeliveredGroup(code: String) {
        val base = orderState.firstOrNull { it.code == code } ?: return
        val customer = findCustomerByPhone(base.phone)
        val basePhone = normalizeCustomerPhone(base.phone)
        val customerPhones = if (customer != null) {
            (listOf(customer.phone) + customer.extraPhones.map { it.number })
                .map(::normalizeCustomerPhone).filter(String::isNotBlank).toSet()
        } else {
            setOf(basePhone).filter(String::isNotBlank).toSet()
        }
        orderState.indices.forEach { i ->
            if (normalizeCustomerPhone(orderState[i].phone) in customerPhones) {
                orderState[i] = orderState[i].copy(status = "Đã giao")
            }
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
                tags=r.service.split(',', ' ').map(String::trim).filter(String::isNotBlank),shop=r.shop,status=r.status)
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
                        val lat = if (a.has("lat") && !a.isNull("lat")) a.optDouble("lat").takeIf { !it.isNaN() }?.toString().orEmpty() else ""
                        val lng = if (a.has("lng") && !a.isNull("lng")) a.optDouble("lng").takeIf { !it.isNaN() }?.toString().orEmpty() else ""
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
                            if (mainLat.isBlank() && a.lat.isNotBlank()) { mainLat = a.lat; changed = true }
                            if (mainLng.isBlank() && a.lng.isNotBlank()) { mainLng = a.lng; changed = true }
                            if (a.photo.isNotBlank() && a.photo != mainPhoto) { mainPhoto = a.photo; changed = true }
                        } else {
                            val ai = extrasAddr.indexOfFirst { normText(it.address) == normText(a.address) }
                            if (ai >= 0) {
                                val old = extrasAddr[ai]
                                val nw = old.copy(
                                    latitude = if (old.latitude.isBlank()) a.lat else old.latitude,
                                    longitude = if (old.longitude.isBlank()) a.lng else old.longitude,
                                    photoUri = if (a.photo.isNotBlank()) a.photo else old.photoUri
                                )
                                if (nw != old) { extrasAddr[ai] = nw; changed = true }
                            } else if (mainAddress.isBlank()) {
                                mainAddress = a.address; mainLat = a.lat; mainLng = a.lng; mainPhoto = a.photo; changed = true
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
                            latitude = mainLat, longitude = mainLng, photoUri = mainPhoto, note = mergedNote,
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
