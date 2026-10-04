package com.example.giaohangpro

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
internal fun CustomerCoordinateMapPicker(
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

    // Picker luôn hiển thị đồng thời vị trí khách đang chọn và vị trí GPS hiện tại.
    // GPS chỉ cập nhật marker người dùng; không đổi selectedPoint và không tự kéo camera sau lần focus đầu.
    fun redrawPickerMarkers(readyMap: MapLibreMap) {
        readyMap.clear()

        driverLocation?.let { userPoint ->
            val userIcon = org.maplibre.android.annotations.IconFactory.getInstance(context)
                .fromBitmap(createDriverMotorbikeBitmap(context))
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(userPoint.latitude, userPoint.longitude))
                    .icon(userIcon)
                    .title("🛵 Vị trí hiện tại")
                    .snippet("GPS người dùng")
            )
        }

        selectedPoint?.let { customerPoint ->
            readyMap.addMarker(
                MarkerOptions()
                    .position(LatLng(customerPoint.latitude, customerPoint.longitude))
                    .title("Vị trí khách hàng đang chọn")
            )
        }
    }

    // Mỗi khi GPS hoặc điểm khách thay đổi, chỉ vẽ lại marker. Không tác động camera.
    LaunchedEffect(driverLocation, selectedPoint, map, styleReady) {
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        redrawPickerMarkers(readyMap)
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
            redrawPickerMarkers(readyMap)
            initialFocusDone = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        // Dialog tạo cửa sổ riêng nên phải tự chừa vùng status/navigation bar.
        // Quy tắc này giữ mọi nút trong màn hình picker nằm giữa thanh thông báo và thanh điều hướng.
        Surface(
            modifier = Modifier.fillMaxSize(),
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

                                        redrawPickerMarkers(readyMap)

                                        readyMap.addOnMapClickListener { tapped ->
                                            selectedPoint = MapPoint(tapped.latitude, tapped.longitude)
                                            redrawPickerMarkers(readyMap)
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
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 58.dp),
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
