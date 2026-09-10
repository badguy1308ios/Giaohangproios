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

private val Orange = Color(0xFFE34B0A)
private val OrangeDark = Color(0xFFC94209)
private val OrangeLight = Color(0xFFFFEEE7)
private val Blue = Color(0xFF168DE2)
private val Navy = Color(0xFF09295A)
private val TextGray = Color(0xFF657894)
private val Background = Color(0xFFF4F8FC)
private val Border = Color(0xFFD7E2EF)
private val CardWhite = Color.White

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
    val note: String = "" // Ghi chú về khách hàng.
)

// Kiểu liên hệ của một số điện thoại, ví dụ Gọi hoặc Zalo.
data class CustomerPhone(
    val number: String, // Số điện thoại cần lưu.
    val action: String = "Gọi" // Nhãn thao tác mặc định của số điện thoại.
)

// Một địa chỉ có thể kèm tọa độ để sau này nối Google Maps.
data class CustomerAddress(
    val address: String, // Chuỗi địa chỉ người dùng nhập.
    val latitude: String = "", // Vĩ độ dạng text để dễ demo/chỉnh sửa.
    val longitude: String = "" // Kinh độ dạng text để dễ demo/chỉnh sửa.
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
enum class AppScreen { MAIN, CUSTOMER_DETAIL, CUSTOMER_FORM }

// Xác định cặp textbox nào trong form sẽ nhận tọa độ sau khi người dùng chọn trên bản đồ.
private enum class CoordinateTarget {
    PRIMARY_ADDRESS, // Cập nhật vĩ độ/kinh độ của địa chỉ chính.
    EXTRA_ADDRESS // Cập nhật vĩ độ/kinh độ của địa chỉ phụ.
}

@Composable
fun GiaoHangApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.MAP) } // Ghi nhớ tab đang được chọn.
    var screen by remember { mutableStateOf(AppScreen.MAIN) } // Ghi nhớ màn hình hiện tại.
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) } // Lưu id khách đang xem/sửa.
    var formIsNew by remember { mutableStateOf(false) } // true = thêm mới, false = sửa khách.

    // Tìm lại khách từ ViewModel theo id để UI luôn nhận dữ liệu mới nhất sau khi lưu.
    val selectedCustomer = selectedCustomerId?.let { id -> vm.findCustomer(id) }

    // Chọn giao diện dựa vào trạng thái điều hướng hiện tại.
    when (screen) {
        AppScreen.MAIN -> {
            Scaffold(
                bottomBar = {
                    BottomTabs(
                        selected = tab, // Truyền tab hiện tại xuống thanh điều hướng.
                        onSelected = { tab = it } // Khi bấm tab mới thì cập nhật state.
                    )
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize() // Chiếm toàn bộ màn hình.
                        .padding(padding) // Chừa vùng cho bottom bar.
                        .background(Background) // Tô màu nền chung.
                ) {
                    when (tab) {
                        Tab.MAP -> MapScreen(vm.orders, vm.customers) // Tab bản đồ dùng Goong Map + OSMDroid, không cần API key.
                        Tab.ORDERS -> OrderListScreen(vm.orders) // Tab đơn hàng.
                        Tab.CUSTOMERS -> CustomerListScreen(
                            customers = vm.customers, // Lấy danh sách có thể cập nhật từ ViewModel.
                            onCustomerClick = { customer ->
                                selectedCustomerId = customer.id // Lưu khách được chọn.
                                screen = AppScreen.CUSTOMER_DETAIL // Mở màn hình chi tiết.
                            },
                            onAddCustomer = {
                                selectedCustomerId = null // Không có khách được chọn khi tạo mới.
                                formIsNew = true // Đánh dấu form đang ở chế độ thêm mới.
                                screen = AppScreen.CUSTOMER_FORM // Mở form.
                            }
                        )
                    }
                }
            }
        }

        AppScreen.CUSTOMER_DETAIL -> {
            // Nếu khách đã bị xóa, quay lại danh sách để tránh null.
            if (selectedCustomer == null) {
                screen = AppScreen.MAIN
            } else {
                CustomerDetailScreen(
                    customer = selectedCustomer, // Hiển thị dữ liệu khách đang chọn.
                    onBack = { screen = AppScreen.MAIN }, // Quay lại tab khách hàng.
                    onEdit = {
                        formIsNew = false // Chuyển sang chế độ sửa.
                        screen = AppScreen.CUSTOMER_FORM // Mở form sửa.
                    },
                    onDelete = {
                        vm.deleteCustomer(selectedCustomer.id) // Xóa khách khỏi danh sách state.
                        selectedCustomerId = null // Bỏ khách đang chọn.
                        screen = AppScreen.MAIN // Quay về danh sách.
                    }
                )
            }
        }

        AppScreen.CUSTOMER_FORM -> {
            CustomerFormScreen(
                customer = if (formIsNew) null else selectedCustomer, // null nghĩa là tạo khách mới.
                onBack = {
                    screen = if (formIsNew) AppScreen.MAIN else AppScreen.CUSTOMER_DETAIL
                },
                onSave = { editedCustomer ->
                    if (formIsNew) {
                        val newId = vm.addCustomer(editedCustomer) // Thêm khách và lấy id mới.
                        selectedCustomerId = newId // Chọn ngay khách vừa tạo.
                    } else {
                        vm.updateCustomer(editedCustomer) // Cập nhật khách cũ.
                        selectedCustomerId = editedCustomer.id // Giữ khách vừa sửa là khách đang chọn.
                    }
                    screen = AppScreen.CUSTOMER_DETAIL // Sau khi lưu mở lại chi tiết.
                }
            )
        }
    }
}

// ================================================================
// 4. HEADER
// ================================================================
@Composable
fun TopHeader() {
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
        Icon(
            Icons.Default.Settings,
            contentDescription = "Cài đặt",
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )

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
            .height(68.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) OrangeLight else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberCircle(marker.number, selected = selected)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (marker.hasRealCoordinate) Blue else Color(0xFFB6C0CC))
                    .clickable(enabled = marker.hasRealCoordinate) { onNavigate() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "Dẫn đường",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(order.code, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(order.customer, color = TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!marker.hasRealCoordinate) {
                    Text("Chưa có tọa độ", color = OrangeDark, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(order.amount, color = OrangeDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    Column(Modifier.fillMaxSize()) {
        TopHeader()

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            SearchBox("Tìm mã đơn, tên người nhận...")

            Spacer(Modifier.height(12.dp))

            Text(
                "Tổng số: 48 đơn",
                color = Navy,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                itemsIndexed(orders) { index, order ->
                    OrderCard(index + 1, order)
                }
            }
        }
    }
}

@Composable
fun OrderCard(index: Int, order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberCircle(index, selected = true)

                Spacer(Modifier.width(8.dp))

                Text(
                    order.code,
                    color = Navy,
                    fontSize = 17.sp,
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

                Icon(Icons.Default.Visibility, null, tint = TextGray)
                Spacer(Modifier.width(7.dp))
                Text(
                    order.amount,
                    color = Navy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))

            OrderInfoRow(Icons.Default.Store, order.customer)
            OrderInfoRow(Icons.Default.Person, "${order.customer} - ${order.phone}")
            OrderInfoRow(Icons.Default.LocationOn, order.address)
            OrderInfoRow(Icons.Default.Inventory2, order.item)

            Spacer(Modifier.height(5.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                order.tags.forEach { Tag(it) }
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("✓  Giao", Icons.Default.CheckCircle, filled = true)
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
            .background(Color(0xFFE1EAF6))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            .height(40.dp)
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Blue else Color.White)
            .border(1.5.dp, Blue, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (filled) Color.White else Blue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                color = if (filled) Color.White else Navy,
                fontSize = 12.sp,
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
                verticalArrangement = Arrangement.spacedBy(7.dp), // Tạo khoảng cách đều giữa các thẻ.
                contentPadding = PaddingValues( // Thêm khoảng trống quanh danh sách.
                    start = 16.dp, // Căn lề trái theo ảnh mẫu.
                    end = 16.dp, // Căn lề phải theo ảnh mẫu.
                    top = 10.dp, // Tạo khoảng cách từ ô tìm kiếm xuống thẻ đầu tiên.
                    bottom = 68.dp // Chừa chỗ để nút + không che thẻ cuối.
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
                .padding(start = 22.dp, bottom = 10.dp), // Đặt khoảng cách giống bố cục ảnh mẫu.
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
            .padding(horizontal = 18.dp, vertical = 10.dp) // Tạo lề giống ảnh mẫu.
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
            .height(136.dp) // Chiều cao đủ cho phần thông tin và 4 nút thao tác.
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
                .padding(horizontal = 12.dp, vertical = 8.dp) // Tạo khoảng cách với viền thẻ.
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

                Spacer(Modifier.width(12.dp)) // Tạo khoảng cách giữa avatar và phần chữ.

                Column(Modifier.weight(1f)) { // Cột tên + số điện thoại chiếm phần ngang còn lại.
                    Text(
                        text = customer.name, // Hiển thị tên khách.
                        color = Color(0xFF1E2C40), // Dùng màu chữ đậm gần ảnh mẫu.
                        fontSize = 17.sp, // Cỡ chữ lớn cho tên.
                        fontWeight = FontWeight.Bold, // Làm tên nổi bật.
                        maxLines = 1, // Không cho tên xuống dòng.
                        overflow = TextOverflow.Ellipsis // Tên dài sẽ hiện dấu ... giống ảnh.
                    )
                    Spacer(Modifier.height(3.dp)) // Tạo khoảng cách nhỏ giữa tên và số điện thoại.
                    Text(
                        text = customer.phone, // Hiển thị số điện thoại.
                        color = TextGray, // Dùng màu xám xanh nhạt hơn tên.
                        fontSize = 15.sp, // Cỡ chữ phù hợp phần phụ.
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

            CustomerQuickActions( // Hàng 4 nút thao tác phía dưới thẻ.
                onZalo = {
                    Toast.makeText(context, "Mở Zalo: ${customer.name}", Toast.LENGTH_SHORT).show() // Phản hồi demo khi bấm Zalo.
                },
                onSms = {
                    Toast.makeText(context, "Soạn SMS: ${customer.phone}", Toast.LENGTH_SHORT).show() // Phản hồi demo khi bấm SMS.
                },
                onCall = {
                    Toast.makeText(context, "Gọi: ${customer.phone}", Toast.LENGTH_SHORT).show() // Phản hồi demo khi bấm Gọi.
                },
                onNavigate = {
                    Toast.makeText(context, "Dẫn đường đến: ${customer.address}", Toast.LENGTH_SHORT).show() // Phản hồi demo khi bấm Dẫn đường.
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
            .size(56.dp) // Kích thước avatar lớn giống ảnh mẫu.
            .clip(CircleShape) // Cắt khung thành hình tròn.
            .background(if (selected) Orange else Color(0xFF35475B)), // Khách đầu màu cam, các khách khác màu xanh xám.
        contentAlignment = Alignment.Center // Căn giữa nội dung avatar.
    ) {
        if (customer.initials.isBlank()) { // Nếu không có chữ viết tắt thì hiển thị icon người.
            Icon(
                imageVector = Icons.Default.Person, // Icon đại diện khách hàng.
                contentDescription = "Ảnh đại diện ${customer.name}", // Mô tả accessibility.
                tint = Color.White, // Icon màu trắng.
                modifier = Modifier.size(30.dp) // Kích thước icon rõ ràng.
            )
        } else { // Nếu có chữ viết tắt thì hiển thị các ký tự đó.
            Text(
                text = customer.initials, // Ví dụ CL, TQ, BH.
                color = Color.White, // Chữ màu trắng.
                fontSize = 20.sp, // Cỡ chữ lớn.
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
        horizontalArrangement = Arrangement.spacedBy(6.dp) // Tạo khoảng cách giữa các nút.
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
            .height(42.dp) // Chiều cao nút giống hàng thao tác trong ảnh.
            .clip(RoundedCornerShape(14.dp)) // Bo góc mềm.
            .background(Color(0xFFF4F1EE)) // Nền xám kem rất nhạt.
            .clickable { onClick() }, // Nhận thao tác chạm.
        contentAlignment = Alignment.Center // Căn giữa hàng icon + chữ.
    ) {
        Row( // Đặt icon và nhãn cạnh nhau.
            modifier = Modifier.padding(horizontal = 5.dp), // Chừa khoảng thở hai bên.
            verticalAlignment = Alignment.CenterVertically // Căn giữa theo chiều dọc.
        ) {
            Box( // Vùng icon hình tròn dùng cho cả bốn nút.
                modifier = Modifier
                    .size(28.dp) // Kích thước vùng icon.
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
                fontSize = 12.sp, // Thu nhỏ nhẹ nhãn dài để không tràn.
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
// Màn hình này được mở khi người dùng bấm vào một thẻ khách trong tab Khách hàng.
@Composable
fun CustomerDetailScreen(
    customer: Customer, // Dữ liệu khách đang được xem.
    onBack: () -> Unit, // Callback quay lại danh sách.
    onEdit: () -> Unit, // Callback mở form sửa khách hàng.
    onDelete: () -> Unit // Callback xóa khách sau khi người dùng xác nhận.
) {
    val context = LocalContext.current // Lấy Context để hiển thị Toast demo.
    var showDeleteDialog by remember { mutableStateOf(false) } // Điều khiển hộp thoại xác nhận xóa.

    // Box giúp đặt cột nút thao tác bên trái và nội dung chi tiết bên phải trên màn hình rộng.
    Box(
        modifier = Modifier
            .fillMaxSize() // Chiếm toàn bộ màn hình.
            .background(Background) // Dùng nền chung của ứng dụng.
    ) {
        Column(Modifier.fillMaxSize()) {
            CustomerPageHeader(
                title = "CHI TIẾT KHÁCH HÀNG", // Tiêu đề giống hình tham chiếu.
                onBack = onBack // Bấm mũi tên để quay lại.
            )

            Row(
                modifier = Modifier
                    .fillMaxSize() // Dùng toàn bộ vùng dưới header.
                    .padding(24.dp), // Tạo lề ngoài cho giao diện.
                horizontalArrangement = Arrangement.spacedBy(24.dp) // Cách cột nút và nội dung.
            ) {
                CustomerSideActions(
                    onCall = { Toast.makeText(context, "Gọi: ${customer.phone}", Toast.LENGTH_SHORT).show() }, // Demo gọi.
                    onZalo = { Toast.makeText(context, "Mở Zalo: ${customer.name}", Toast.LENGTH_SHORT).show() }, // Demo Zalo.
                    onSms = { Toast.makeText(context, "Nhắn tin: ${customer.phone}", Toast.LENGTH_SHORT).show() }, // Demo SMS.
                    onEdit = onEdit, // Mở màn hình sửa.
                    onDelete = { showDeleteDialog = true } // Mở hộp thoại xác nhận xóa.
                )

                CustomerDetailContent(
                    customer = customer, // Truyền toàn bộ dữ liệu vào phần nội dung.
                    modifier = Modifier.weight(1f) // Nội dung chiếm toàn bộ chiều ngang còn lại.
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false }, // Chạm ngoài hộp thoại thì đóng.
                title = { Text("Xóa khách hàng") }, // Tiêu đề xác nhận.
                text = { Text("Bạn có chắc muốn xóa ${customer.name} không?") }, // Nội dung cảnh báo.
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false // Đóng dialog trước.
                        onDelete() // Thực hiện xóa.
                    }) { Text("Xóa", color = Color(0xFFE21B1B)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") }
                }
            )
        }
    }
}

@Composable
private fun CustomerPageHeader(
    title: String, // Nội dung tiêu đề cần hiển thị.
    onBack: () -> Unit // Callback quay lại màn hình trước.
) {
    Row(
        modifier = Modifier
            .fillMaxWidth() // Header rộng toàn màn hình.
            .height(82.dp) // Chiều cao gần giống ảnh mẫu.
            .background(Brush.horizontalGradient(listOf(OrangeDark, Orange))) // Gradient cam/đỏ.
            .padding(horizontal = 20.dp), // Tạo khoảng cách hai bên.
        verticalAlignment = Alignment.CenterVertically // Căn giữa icon và chữ.
    ) {
        IconButton(onClick = onBack) { // Nút mũi tên quay lại.
            Icon(
                imageVector = Icons.Default.ArrowBack, // Icon quay lại.
                contentDescription = "Quay lại", // Mô tả accessibility.
                tint = Color.White, // Icon trắng.
                modifier = Modifier.size(34.dp) // Kích thước dễ chạm.
            )
        }
        Spacer(Modifier.width(20.dp)) // Khoảng cách giữa mũi tên và tiêu đề.
        Text(
            text = title, // Hiển thị tiêu đề truyền vào.
            color = Color.White, // Chữ trắng.
            fontSize = 24.sp, // Cỡ chữ lớn.
            fontWeight = FontWeight.ExtraBold // Làm tiêu đề nổi bật.
        )
    }
}

@Composable
private fun CustomerSideActions(
    onCall: () -> Unit, // Hành động Gọi.
    onZalo: () -> Unit, // Hành động Zalo.
    onSms: () -> Unit, // Hành động Nhắn tin.
    onEdit: () -> Unit, // Hành động Sửa.
    onDelete: () -> Unit // Hành động Xóa.
) {
    Column(
        modifier = Modifier.width(156.dp), // Cố định độ rộng cột nút giống bố cục ảnh.
        verticalArrangement = Arrangement.spacedBy(14.dp) // Khoảng cách giữa các nút.
    ) {
        DetailActionButton("Gọi", Icons.Default.Call, Blue, onCall) // Nút gọi.
        DetailActionButton("Zalo", Icons.Default.Chat, Blue, onZalo) // Nút Zalo.
        DetailActionButton("Nhắn tin", Icons.Default.ChatBubbleOutline, Blue, onSms) // Nút nhắn tin.
        DetailActionButton("Sửa", Icons.Default.Edit, Blue, onEdit) // Nút sửa.
        DetailActionButton("Xóa", Icons.Default.DeleteOutline, Color(0xFFE21B1B), onDelete) // Nút xóa màu đỏ.
    }
}

@Composable
private fun DetailActionButton(
    label: String, // Chữ trên nút.
    icon: androidx.compose.ui.graphics.vector.ImageVector, // Icon minh họa.
    color: Color, // Màu nền nút.
    onClick: () -> Unit // Callback khi bấm.
) {
    Card(
        modifier = Modifier
            .fillMaxWidth() // Nút rộng theo cột.
            .height(150.dp) // Nút lớn, phù hợp thao tác cảm ứng.
            .clickable { onClick() }, // Nhận sự kiện bấm.
        shape = RoundedCornerShape(24.dp), // Bo góc mềm.
        colors = CardDefaults.cardColors(containerColor = color) // Tô màu theo tham số.
    ) {
        Column(
            modifier = Modifier.fillMaxSize(), // Chiếm toàn bộ nút.
            horizontalAlignment = Alignment.CenterHorizontally, // Căn giữa ngang.
            verticalArrangement = Arrangement.Center // Căn giữa dọc.
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(48.dp)) // Icon trắng.
            Spacer(Modifier.height(14.dp)) // Khoảng cách icon/chữ.
            Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) // Nhãn nút.
        }
    }
}

@Composable
private fun CustomerDetailContent(
    customer: Customer, // Dữ liệu khách cần hiển thị.
    modifier: Modifier = Modifier // Cho phép màn hình cha truyền modifier.
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()) // Cho phép cuộn khi màn hình thấp.
            .padding(bottom = 24.dp), // Chừa khoảng cuối nội dung.
        verticalArrangement = Arrangement.spacedBy(20.dp) // Cách đều các card.
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), // Card rộng toàn bộ phần nội dung.
            shape = RoundedCornerShape(24.dp), // Bo góc giống mẫu.
            colors = CardDefaults.cardColors(containerColor = Color.White), // Nền trắng.
            border = androidx.compose.foundation.BorderStroke(1.dp, Border) // Viền mảnh.
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth() // Rộng toàn bộ card.
                    .padding(36.dp), // Tạo khoảng thở bên trong.
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally) // Căn avatar vào giữa.
                        .size(150.dp) // Avatar lớn như ảnh tham chiếu.
                        .clip(CircleShape) // Tạo hình tròn.
                        .background(Orange), // Nền cam thương hiệu.
                    contentAlignment = Alignment.Center // Căn icon vào giữa.
                ) {
                    Icon(Icons.Default.Storefront, "Biểu tượng cửa hàng", tint = Color.White, modifier = Modifier.size(78.dp))
                }
            }
        }

        DetailInfoCard(Icons.Default.Person, customer.name) // Card hiển thị tên.
        DetailInfoCard(Icons.Default.Phone, customer.phone) // Card hiển thị số điện thoại.

        Card(
            modifier = Modifier.fillMaxWidth(), // Card địa chỉ rộng toàn phần.
            shape = RoundedCornerShape(24.dp), // Bo góc.
            colors = CardDefaults.cardColors(containerColor = Color.White), // Nền trắng.
            border = androidx.compose.foundation.BorderStroke(1.dp, Border) // Viền mảnh.
        ) {
            Row(
                modifier = Modifier.padding(28.dp), // Khoảng cách trong card.
                verticalAlignment = Alignment.CenterVertically // Căn giữa icon và nội dung.
            ) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Orange), // Avatar tròn cho địa chỉ.
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
                Spacer(Modifier.width(24.dp)) // Khoảng cách icon/nội dung.
                Column(Modifier.weight(1f)) {
                    Text(customer.address, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold) // Địa chỉ chính.
                    Spacer(Modifier.height(12.dp)) // Cách dòng trạng thái.
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1F4F8)).padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Nếu đã có tọa độ đã lưu thì hiển thị trực tiếp để kiểm tra nhanh.
                        Text(
                            if (customer.latitude.isBlank() || customer.longitude.isBlank()) "• Chưa có tọa độ" else "• ${customer.latitude}, ${customer.longitude}",
                            color = TextGray,
                            fontSize = 15.sp
                        )
                    } // Trạng thái tọa độ thật của khách hàng.
                }
                Icon(Icons.Default.NearMe, "Dẫn đường", tint = Blue, modifier = Modifier.size(40.dp)) // Icon dẫn đường.
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), // Card ghi chú rộng toàn phần.
            shape = RoundedCornerShape(24.dp), // Bo góc.
            colors = CardDefaults.cardColors(containerColor = Color.White), // Nền trắng.
            border = androidx.compose.foundation.BorderStroke(1.dp, Border) // Viền mảnh.
        ) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = TextGray) // Icon ghi chú.
                    Spacer(Modifier.width(14.dp)) // Khoảng cách icon/chữ.
                    Text("GHI CHÚ", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold) // Tiêu đề ghi chú.
                }
                Spacer(Modifier.height(18.dp)) // Cách phần nội dung.
                Text(
                    text = customer.note.ifBlank { "Thêm ghi chú..." }, // Nếu chưa có note thì hiển thị placeholder.
                    color = if (customer.note.isBlank()) TextGray else Navy, // Đổi màu placeholder.
                    fontSize = 17.sp // Cỡ chữ dễ đọc.
                )
            }
        }
    }
}

@Composable
private fun DetailInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, // Icon ở bên trái.
    value: String // Nội dung thông tin cần hiển thị.
) {
    Card(
        modifier = Modifier.fillMaxWidth(), // Card rộng toàn phần.
        shape = RoundedCornerShape(24.dp), // Bo góc.
        colors = CardDefaults.cardColors(containerColor = Color.White), // Nền trắng.
        border = androidx.compose.foundation.BorderStroke(1.dp, Border) // Viền mảnh.
    ) {
        Row(
            modifier = Modifier.padding(28.dp), // Khoảng cách trong card.
            verticalAlignment = Alignment.CenterVertically // Căn giữa.
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Orange), // Vòng tròn icon màu cam.
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.width(24.dp)) // Khoảng cách icon/chữ.
            Text(value, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold) // Giá trị thông tin.
        }
    }
}

// ================================================================
// 9. THÊM / SỬA KHÁCH HÀNG
// ================================================================
// Một form dùng chung cho cả hai trường hợp: customer = null thì thêm mới, ngược lại là sửa.
@Composable
fun CustomerFormScreen(
    customer: Customer?, // Khách cần sửa, null nếu đang tạo khách mới.
    onBack: () -> Unit, // Callback quay lại.
    onSave: (Customer) -> Unit // Callback trả dữ liệu đã lưu về GiaoHangApp/ViewModel.
) {
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) } // State tên chính.
    var aliasText by remember(customer?.id) { mutableStateOf(customer?.aliases?.joinToString("\n").orEmpty()) } // Tên phụ, mỗi dòng một tên.
    var phone by remember(customer?.id) { mutableStateOf(customer?.phone.orEmpty()) } // Số điện thoại chính.
    var extraPhone by remember(customer?.id) { mutableStateOf(customer?.extraPhones?.firstOrNull()?.number.orEmpty()) } // Số phụ đầu tiên.
    var extraPhoneAction by remember(customer?.id) { mutableStateOf(customer?.extraPhones?.firstOrNull()?.action ?: "Zalo") } // Nhãn số phụ.
    var address by remember(customer?.id) { mutableStateOf(customer?.address.orEmpty()) } // Địa chỉ chính.
    var latitude by remember(customer?.id) { mutableStateOf(customer?.latitude.orEmpty()) } // Vĩ độ chính đã lưu hoặc người dùng chọn trên Goong Map.
    var longitude by remember(customer?.id) { mutableStateOf(customer?.longitude.orEmpty()) } // Kinh độ chính đã lưu hoặc người dùng chọn trên Goong Map.
    var extraAddress by remember(customer?.id) { mutableStateOf(customer?.extraAddresses?.firstOrNull()?.address.orEmpty()) } // Địa chỉ phụ.
    var extraLatitude by remember(customer?.id) { mutableStateOf(customer?.extraAddresses?.firstOrNull()?.latitude.orEmpty()) } // Vĩ độ địa chỉ phụ.
    var extraLongitude by remember(customer?.id) { mutableStateOf(customer?.extraAddresses?.firstOrNull()?.longitude.orEmpty()) } // Kinh độ địa chỉ phụ.
    var note by remember(customer?.id) { mutableStateOf(customer?.note.orEmpty()) } // Ghi chú.
    var showValidationError by remember { mutableStateOf(false) } // Điều khiển thông báo bắt buộc.
    // State mở/đóng bản đồ chọn tọa độ bằng thao tác ghim vị trí.
    var showCoordinatePicker by remember { mutableStateOf(false) }
    // Ghi nhớ người dùng đang chọn tọa độ cho địa chỉ chính hay địa chỉ phụ.
    var coordinateTarget by remember { mutableStateOf(CoordinateTarget.PRIMARY_ADDRESS) }
    var geocodeRequest by remember { mutableStateOf<Pair<CoordinateTarget, String>?>(null) }
    var geocodeMessage by remember { mutableStateOf("") }
    var geocodeLoading by remember { mutableStateOf(false) }

    val isEdit = customer != null // Xác định chế độ hiện tại.

    Column(
        modifier = Modifier
            .fillMaxSize() // Chiếm toàn màn hình.
            .background(Background) // Màu nền chung.
    ) {
        CustomerPageHeader(
            title = if (isEdit) "SỬA THÔNG TIN KHÁCH HÀNG" else "THÊM KHÁCH HÀNG", // Đổi tiêu đề theo chế độ.
            onBack = onBack // Quay lại màn hình trước.
        )

        Column(
            modifier = Modifier
                .weight(1f) // Chiếm vùng có thể cuộn.
                .verticalScroll(rememberScrollState()) // Cho phép cuộn form dài.
                .padding(24.dp), // Lề ngoài form.
            verticalArrangement = Arrangement.spacedBy(20.dp) // Cách đều các khối.
        ) {
            CustomerPhotoCard() // Khung ảnh đại diện theo bố cục ảnh tham chiếu.

            FormSection(title = "THÔNG TIN KHÁCH HÀNG") {
                LabeledInput("Tên khách hàng", name, { name = it }, "Nhập tên khách hàng") // Nhập tên chính.
                LabeledInput("Tên phụ / tên cửa hàng", aliasText, { aliasText = it }, "Mỗi dòng là một tên") // Nhập tên phụ.
            }

            FormSection(title = "SỐ ĐIỆN THOẠI") {
                LabeledInput(
                    label = "Số điện thoại chính", // Nhãn số chính.
                    value = phone, // State số chính.
                    onValueChange = { phone = it }, // Cập nhật state.
                    placeholder = "Nhập số điện thoại", // Gợi ý nhập.
                    keyboardType = KeyboardType.Phone // Bàn phím số điện thoại.
                )
                LabeledInput(
                    label = "Số điện thoại phụ (${extraPhoneAction})", // Hiển thị loại thao tác.
                    value = extraPhone, // State số phụ.
                    onValueChange = { extraPhone = it }, // Cập nhật state.
                    placeholder = "Nhập số điện thoại phụ", // Gợi ý.
                    keyboardType = KeyboardType.Phone // Bàn phím số.
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(onClick = { extraPhoneAction = "Gọi" }, label = { Text("Gọi") }) // Chọn loại gọi.
                    AssistChip(onClick = { extraPhoneAction = "Zalo" }, label = { Text("Zalo") }) // Chọn loại Zalo.
                }
            }

            FormSection(title = "ĐỊA CHỈ GIAO HÀNG") {
                LabeledInput("Địa chỉ", address, { address = it }, "Nhập địa chỉ giao hàng") // Địa chỉ chính.
                CoordinateRow(
                    latitude = latitude,
                    onLatitudeChange = { latitude = it },
                    longitude = longitude,
                    onLongitudeChange = { longitude = it },
                    onPickOnMap = {
                        coordinateTarget = CoordinateTarget.PRIMARY_ADDRESS
                        showCoordinatePicker = true
                    }
                )
                TextButton(
                    onClick = {
                        if (address.isBlank()) {
                            geocodeMessage = "Vui lòng nhập địa chỉ trước."
                        } else {
                            geocodeRequest = CoordinateTarget.PRIMARY_ADDRESS to address
                            geocodeMessage = "Đang lấy tọa độ từ Goong..."
                        }
                    },
                    enabled = !geocodeLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LẤY TỌA ĐỘ TỪ ĐỊA CHỈ", fontWeight = FontWeight.Bold)
                }
                LabeledInput("Địa chỉ phụ", extraAddress, { extraAddress = it }, "Nhập địa chỉ phụ")
                CoordinateRow(
                    latitude = extraLatitude,
                    onLatitudeChange = { extraLatitude = it },
                    longitude = extraLongitude,
                    onLongitudeChange = { extraLongitude = it },
                    onPickOnMap = {
                        coordinateTarget = CoordinateTarget.EXTRA_ADDRESS
                        showCoordinatePicker = true
                    }
                )
                TextButton(
                    onClick = {
                        if (extraAddress.isBlank()) {
                            geocodeMessage = "Vui lòng nhập địa chỉ phụ trước."
                        } else {
                            geocodeRequest = CoordinateTarget.EXTRA_ADDRESS to extraAddress
                            geocodeMessage = "Đang lấy tọa độ địa chỉ phụ từ Goong..."
                        }
                    },
                    enabled = !geocodeLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LẤY TỌA ĐỘ ĐỊA CHỈ PHỤ", fontWeight = FontWeight.Bold)
                }
                if (geocodeMessage.isNotBlank()) {
                    Text(
                        geocodeMessage,
                        color = if (geocodeMessage.startsWith("Đang")) Blue else Navy,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            FormSection(title = "GHI CHÚ") {
                LabeledInput(
                    label = "Ghi chú", // Nhãn ghi chú.
                    value = note, // State ghi chú.
                    onValueChange = { note = it }, // Cập nhật state.
                    placeholder = "Ví dụ: Giao giờ hành chính, gọi trước 15 phút", // Gợi ý.
                    singleLine = false, // Cho phép nhiều dòng.
                    minLines = 4 // Tạo vùng nhập lớn.
                )
            }

            if (showValidationError) {
                Text("Vui lòng nhập tối thiểu tên, số điện thoại và địa chỉ.", color = Color(0xFFE21B1B), fontWeight = FontWeight.SemiBold) // Báo lỗi dữ liệu bắt buộc.
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth() // Hàng nút rộng toàn màn hình.
                .background(Color.White) // Nền trắng cho thanh thao tác.
                .padding(20.dp), // Lề trong thanh nút.
            horizontalArrangement = Arrangement.spacedBy(16.dp) // Cách hai nút.
        ) {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank() || address.isBlank()) {
                        showValidationError = true // Thiếu dữ liệu thì hiển thị lỗi.
                    } else {
                        val aliases = aliasText.lines().map { it.trim() }.filter { it.isNotBlank() } // Tách tên phụ theo dòng.
                        val extraPhones = if (extraPhone.isBlank()) emptyList() else listOf(CustomerPhone(extraPhone.trim(), extraPhoneAction)) // Chỉ tạo số phụ khi có dữ liệu.
                        val extraAddresses = if (extraAddress.isBlank()) emptyList() else listOf(CustomerAddress(extraAddress.trim(), extraLatitude.trim(), extraLongitude.trim())) // Chỉ tạo địa chỉ phụ khi có dữ liệu.
                        onSave(
                            Customer(
                                id = customer?.id ?: 0L, // Khách mới dùng id 0 để ViewModel cấp id thật.
                                name = name.trim(), // Loại bỏ khoảng trắng thừa.
                                phone = phone.trim(), // Loại bỏ khoảng trắng thừa.
                                address = address.trim(), // Loại bỏ khoảng trắng thừa.
                                latitude = latitude.trim(), // Lưu vĩ độ chính do người dùng chọn hoặc nhập.
                                longitude = longitude.trim(), // Lưu kinh độ chính do người dùng chọn hoặc nhập.
                                initials = createInitials(name), // Tự tạo chữ viết tắt cho avatar.
                                aliases = aliases, // Lưu tên phụ.
                                extraPhones = extraPhones, // Lưu số phụ.
                                extraAddresses = extraAddresses, // Lưu địa chỉ phụ.
                                note = note.trim() // Lưu ghi chú.
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f).height(58.dp), // Nút Lưu chiếm phần lớn chiều ngang.
                shape = RoundedCornerShape(18.dp), // Bo góc.
                colors = ButtonDefaults.buttonColors(containerColor = Blue) // Nền xanh giống ảnh.
            ) { Text("LƯU THAY ĐỔI", fontSize = 18.sp, fontWeight = FontWeight.Bold) } // Nhãn nút lưu.

            OutlinedButton(
                onClick = onBack, // Hủy và quay lại không lưu.
                modifier = Modifier.height(58.dp), // Đồng bộ chiều cao với nút lưu.
                shape = RoundedCornerShape(18.dp) // Bo góc.
            ) { Text("Hủy", fontSize = 18.sp, fontWeight = FontWeight.Bold) } // Nhãn nút hủy.
        }
    }

    LaunchedEffect(geocodeRequest) {
        val request = geocodeRequest ?: return@LaunchedEffect
        geocodeLoading = true
        geocodeMessage = "Đang lấy tọa độ từ Goong..."
        val point = geocodeAddressWithGoong(request.second)
        geocodeLoading = false
        if (point != null) {
            val latText = "%.6f".format(java.util.Locale.US, point.latitude)
            val lngText = "%.6f".format(java.util.Locale.US, point.longitude)
            when (request.first) {
                CoordinateTarget.PRIMARY_ADDRESS -> {
                    latitude = latText
                    longitude = lngText
                    geocodeMessage = "Đã lấy tọa độ khách hàng từ Goong."
                }
                CoordinateTarget.EXTRA_ADDRESS -> {
                    extraLatitude = latText
                    extraLongitude = lngText
                    geocodeMessage = "Đã lấy tọa độ địa chỉ phụ từ Goong."
                }
            }
        } else {
            geocodeMessage = "Goong không tìm thấy tọa độ cho địa chỉ này."
        }
        geocodeRequest = null
    }

    // Dialog bản đồ chỉ xuất hiện khi người dùng bấm nút ghim bên cạnh hai textbox tọa độ.
    if (showCoordinatePicker) {
        CustomerCoordinateMapPicker(
            // Ưu tiên mở đúng tọa độ đang có trong textbox của địa chỉ đang chỉnh sửa.
            initialPoint = when (coordinateTarget) {
                CoordinateTarget.PRIMARY_ADDRESS -> pointFromStrings(latitude, longitude)
                CoordinateTarget.EXTRA_ADDRESS -> pointFromStrings(extraLatitude, extraLongitude)
            },
            // Đóng bản đồ mà không thay đổi vĩ độ/kinh độ.
            onDismiss = { showCoordinatePicker = false },
            // Khi nhấn LƯU TỌA ĐỘ trong dialog, ghi điểm đã chọn vào đúng cặp textbox.
            onSavePoint = { point ->
                val latText = "%.6f".format(java.util.Locale.US, point.latitude) // Chuẩn hóa vĩ độ thành 6 chữ số thập phân.
                val lngText = "%.6f".format(java.util.Locale.US, point.longitude) // Chuẩn hóa kinh độ thành 6 chữ số thập phân.
                when (coordinateTarget) {
                    CoordinateTarget.PRIMARY_ADDRESS -> {
                        latitude = latText // Ghi vào textbox vĩ độ chính.
                        longitude = lngText // Ghi vào textbox kinh độ chính.
                    }
                    CoordinateTarget.EXTRA_ADDRESS -> {
                        extraLatitude = latText // Ghi vào textbox vĩ độ phụ.
                        extraLongitude = lngText // Ghi vào textbox kinh độ phụ.
                    }
                }
                showCoordinatePicker = false // Đóng dialog và quay lại form.
            }
        )
    }
}

@Composable
private fun CustomerPhotoCard() {
    Card(
        modifier = Modifier.fillMaxWidth().height(280.dp), // Khung ảnh lớn.
        shape = RoundedCornerShape(24.dp), // Bo góc.
        colors = CardDefaults.cardColors(containerColor = Color.White), // Nền trắng khi chưa có ảnh thật.
        border = androidx.compose.foundation.BorderStroke(1.dp, Border) // Viền mảnh.
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Storefront, "Ảnh khách hàng", tint = Orange, modifier = Modifier.size(96.dp)) // Placeholder cửa hàng.
                Spacer(Modifier.height(16.dp)) // Cách icon/chữ.
                Text("Ảnh khách hàng", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold) // Nhãn ảnh.
                Spacer(Modifier.height(12.dp)) // Cách nút.
                OutlinedButton(onClick = { /* TODO: nối Photo Picker Android */ }) {
                    Icon(Icons.Default.Image, null) // Icon đổi ảnh.
                    Spacer(Modifier.width(8.dp)) // Khoảng cách icon/chữ.
                    Text("Đổi ảnh") // Nhãn nút.
                }
            }
        }
    }
}

@Composable
private fun FormSection(
    title: String, // Tiêu đề nhóm thông tin.
    content: @Composable ColumnScope.() -> Unit // Nội dung tùy biến bên trong nhóm.
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold) // Tiêu đề nhóm.
        Card(
            modifier = Modifier.fillMaxWidth(), // Card rộng toàn phần.
            shape = RoundedCornerShape(22.dp), // Bo góc.
            colors = CardDefaults.cardColors(containerColor = Color.White), // Nền trắng.
            border = androidx.compose.foundation.BorderStroke(1.dp, Border) // Viền mảnh.
        ) {
            Column(
                modifier = Modifier.padding(20.dp), // Lề trong card.
                verticalArrangement = Arrangement.spacedBy(14.dp), // Cách các ô nhập.
                content = content // Chèn các composable do nơi gọi truyền vào.
            )
        }
    }
}

@Composable
private fun LabeledInput(
    label: String, // Nhãn phía trên ô nhập.
    value: String, // Giá trị hiện tại.
    onValueChange: (String) -> Unit, // Callback cập nhật giá trị.
    placeholder: String, // Gợi ý nhập.
    keyboardType: KeyboardType = KeyboardType.Text, // Kiểu bàn phím mặc định.
    singleLine: Boolean = true, // Mặc định một dòng.
    minLines: Int = 1 // Số dòng tối thiểu.
) {
    Column {
        Text(label, color = TextGray, fontSize = 15.sp, fontWeight = FontWeight.Medium) // Hiển thị nhãn.
        Spacer(Modifier.height(6.dp)) // Cách nhãn và input.
        OutlinedTextField(
            value = value, // Hiển thị state.
            onValueChange = onValueChange, // Cập nhật state khi gõ.
            modifier = Modifier.fillMaxWidth(), // Input rộng toàn phần.
            placeholder = { Text(placeholder) }, // Chữ gợi ý.
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType), // Thiết lập bàn phím.
            singleLine = singleLine, // Cho phép/chặn xuống dòng.
            minLines = minLines, // Số dòng tối thiểu.
            shape = RoundedCornerShape(16.dp), // Bo góc input.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue, // Viền khi focus.
                unfocusedBorderColor = Border, // Viền khi chưa focus.
                focusedContainerColor = Color.White, // Nền khi focus.
                unfocusedContainerColor = Color.White // Nền khi không focus.
            )
        )
    }
}

@Composable
private fun CoordinateRow(
    latitude: String, // Giá trị vĩ độ.
    onLatitudeChange: (String) -> Unit, // Callback vĩ độ.
    longitude: String, // Giá trị kinh độ.
    onLongitudeChange: (String) -> Unit, // Callback kinh độ.
    onPickOnMap: (() -> Unit)? = null // Callback mở bản đồ chọn điểm; null nếu không cần nút ghim.
) {
    // Ba cột: textbox vĩ độ, textbox kinh độ và nút ghim bản đồ ở bên phải.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
            LabeledInput("Vĩ độ", latitude, onLatitudeChange, "10.775255", KeyboardType.Decimal) // Ô vĩ độ.
        }
        Box(Modifier.weight(1f)) {
            LabeledInput("Kinh độ", longitude, onLongitudeChange, "106.701479", KeyboardType.Decimal) // Ô kinh độ.
        }
        // Chỉ hiện nút ghim khi nơi gọi truyền callback chọn bản đồ.
        if (onPickOnMap != null) {
            IconButton(
                onClick = onPickOnMap, // Mở bản đồ chọn tọa độ.
                modifier = Modifier
                    .size(56.dp) // Kích thước đủ lớn để dễ bấm trên điện thoại.
                    .clip(RoundedCornerShape(16.dp)) // Bo góc đồng bộ với textbox.
                    .background(OrangeLight) // Nền cam nhạt để nổi bật chức năng định vị.
                    .border(1.dp, Orange, RoundedCornerShape(16.dp)) // Viền cam.
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn, // Biểu tượng ghim định vị.
                    contentDescription = "Chọn tọa độ trên bản đồ",
                    tint = Orange, // Màu icon thương hiệu.
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Tạo chữ viết tắt từ tên để hiển thị trong avatar, ví dụ "Cửa hàng Minh Tâm" -> "CT".
private fun createInitials(name: String): String {
    return name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

// ================================================================
// 9. THANH 3 TAB DƯỚI CÙNG
// ================================================================
@Composable
fun BottomTabs(selected: Tab, onSelected: (Tab) -> Unit) {
    NavigationBar(
        modifier = Modifier.height(48.dp),
        containerColor = Orange,
        tonalElevation = 0.dp
    ) {
        BottomTabItem(
            selected == Tab.MAP,
            "Bản đồ",
            Icons.Default.Map,
            { onSelected(Tab.MAP) }
        )
        BottomTabItem(
            selected == Tab.ORDERS,
            "Chi tiết đơn",
            Icons.Default.ReceiptLong,
            { onSelected(Tab.ORDERS) }
        )
        BottomTabItem(
            selected == Tab.CUSTOMERS,
            "Khách hàng",
            Icons.Default.People,
            { onSelected(Tab.CUSTOMERS) }
        )
    }
}

@Composable
fun RowScope.BottomTabItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        },
        label = {
            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Orange,
            selectedTextColor = Orange,
            indicatorColor = Color.White,
            unselectedIconColor = Color.White,
            unselectedTextColor = Color.White
        )
    )
}

// ================================================================
// 10. Ô TÌM KIẾM + SỐ THỨ TỰ
// ================================================================
@Composable
fun SearchBox(placeholder: String) {
    OutlinedTextField(
        value = "",
        onValueChange = { /* TODO: nối vào state tìm kiếm */ },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, color = TextGray, fontSize = 17.sp)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, null, tint = TextGray, modifier = Modifier.size(30.dp))
        },
        trailingIcon = {
            Icon(Icons.Default.Close, null, tint = TextGray)
        },
        shape = RoundedCornerShape(18.dp),
        singleLine = false,
        minLines = 1,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Blue,
            unfocusedBorderColor = Border,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun NumberCircle(number: Int, selected: Boolean) {
    Box(
        Modifier
            .size(24.dp) // Bong bóng STT nhỏ gọn 24dp.
            .clip(CircleShape)
            .background(if (selected) Orange else Color(0xFF4E5B6B)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = Color.White,
            fontSize = 12.sp, // Số STT 12sp.
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
