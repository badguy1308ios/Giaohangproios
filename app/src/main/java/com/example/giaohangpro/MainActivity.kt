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
import androidx.lifecycle.viewModelScope
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
internal val CardWhite = Color.White
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

internal fun fmtMoney(v: Long): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v)

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
        val photoSources = customerState.flatMap { c -> listOf(c.photoUri) + c.extraAddresses.map { it.photoUri } }
        viewModelScope.launch(Dispatchers.IO) { preserveLegacyGatePhotos(getApplication(), photoSources) }
        true
    }.getOrElse { false }

    private fun savePersistentData(editor: android.content.SharedPreferences.Editor = prefs.edit(), commitNow: Boolean = false): Boolean {
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
        editor.putString("orders",ordersArr.toString()).putString("customers",customersArr.toString())
        return if (commitNow) editor.commit() else { editor.apply(); true }
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
        val previous = customerState[index]
        if (previous.photoUri == uri) return
        customerState[index] = CustomerCoordinateSafety.updatePhoto(previous, uri)
        if (savePersistentData(commitNow = true)) {
            viewModelScope.launch(Dispatchers.IO) { deleteStoredGatePhoto(getApplication(), previous.photoUri) }
        } else {
            customerState[index] = previous
            savePersistentData()
            Toast.makeText(getApplication(), "Chưa lưu được ảnh mới. Ảnh cũ được giữ nguyên.", Toast.LENGTH_LONG).show()
        }
    }

    fun updateCustomer(updatedCustomer: Customer, original: Customer): Boolean {
        val index = customerState.indexOfFirst { it.id == updatedCustomer.id }
        if (index < 0 || !CustomerCoordinateSafety.canSave(customerState[index], original)) return false
        addStreetName(updatedCustomer.streetName)
        customerState[index] = updatedCustomer
        if (!savePersistentData(commitNow = true)) {
            customerState[index] = original
            savePersistentData()
            return false
        }
        val retained = (listOf(updatedCustomer.photoUri) + updatedCustomer.extraAddresses.map { it.photoUri }).toSet()
        val replaced = (listOf(original.photoUri) + original.extraAddresses.map { it.photoUri })
            .filter { it.isNotBlank() && it !in retained }.distinct()
        viewModelScope.launch(Dispatchers.IO) { replaced.forEach { deleteStoredGatePhoto(getApplication(), it) } }
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
                check(source.isFile && source.length() > 0) { "ZIP thiếu ảnh $path; chưa thay dữ liệu khách." }
                check(gatePhotoBackupBytes(context, android.net.Uri.fromFile(source).toString()) != null) {
                    "Ảnh trong ZIP bị hỏng; chưa thay dữ liệu khách."
                }
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
            // Resolve and decode every referenced photo before opening/truncating
            // the destination ZIP. Never export dangling photo references.
            val photoTemp = java.io.File(context.cacheDir, "photo_export_${java.util.UUID.randomUUID()}")
            check(photoTemp.mkdirs()) { "Không tạo được thư mục tạm cho ảnh" }
            try {
                val packedPhotos = photoJobs.mapIndexed { index, job ->
                    val bytes = gatePhotoBackupBytes(context, job.source)
                        ?: error("Có ảnh khách không đọc được. Chưa ghi đè file sao lưu cũ.")
                    val file = java.io.File(photoTemp, "$index.webp").apply { writeBytes(bytes) }
                    job.zipPath to file
                }

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
                        packedPhotos.forEach { (path, file) ->
                            writeEntry(path, file.readBytes())
                            writtenImages++
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
            } finally { photoTemp.deleteRecursively() }
        }
    }

}
