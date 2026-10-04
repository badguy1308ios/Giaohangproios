package com.example.giaohangpro

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

private data class GroupedMarkerNumber(
    val number: Int,
    val isPending: Boolean,
    val selected: Boolean
)

// Vẽ nhiều STT trong cùng một marker nhưng chỉ dùng một mũi ghim tại tọa độ thật.
// Mỗi hàng tối đa 4 STT; số lượng lớn sẽ tự xuống hàng để không che mất marker bên dưới.
private fun createGroupedNumberBubbleDrawable(
    context: android.content.Context,
    markers: List<MapOrderMarker>,
    selectedOrderNumber: Int?
): android.graphics.drawable.Drawable {
    val visibleNumbers = markers
        .filter { it.showNumber }
        .distinctBy { it.number }
        .sortedBy { it.number }
        .map {
            GroupedMarkerNumber(
                number = it.number,
                isPending = !it.hasRealCoordinate,
                selected = it.number == selectedOrderNumber
            )
        }

    if (visibleNumbers.size <= 1) {
        val marker = markers.first()
        return createNumberBubbleDrawable(
            context = context,
            number = marker.number,
            isPending = !marker.hasRealCoordinate,
            showNumber = marker.showNumber,
            selected = marker.number == selectedOrderNumber
        )
    }

    val density = context.resources.displayMetrics.density
    val columns = visibleNumbers.size.coerceAtMost(4)
    val rows = (visibleNumbers.size + columns - 1) / columns
    val cell = 30f * density
    val gap = 3f * density
    val padding = 4f * density
    val bodyWidth = padding * 2f + columns * cell + (columns - 1) * gap
    val bodyHeight = padding * 2f + rows * cell + (rows - 1) * gap
    val pointerHeight = 11f * density
    val pointerTipY = bodyHeight + pointerHeight
    // MapLibre neo bitmap theo tâm; phần trong suốt phía dưới giữ đầu mũi ghim đúng tọa độ.
    val bitmapWidth = bodyWidth.toInt().coerceAtLeast((34f * density).toInt())
    val bitmapHeight = (pointerTipY * 2f).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(
        bitmapWidth,
        bitmapHeight,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val groupColor = when {
        visibleNumbers.any { it.selected } -> android.graphics.Color.rgb(22, 141, 226)
        visibleNumbers.all { it.isPending } -> android.graphics.Color.rgb(117, 132, 153)
        else -> android.graphics.Color.rgb(227, 75, 10)
    }

    // Nền trắng và viền giúp từng STT vẫn rõ khi nằm trên đường hoặc khu dân cư dày.
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.argb(245, 255, 255, 255)
    val bodyRect = android.graphics.RectF(0f, 0f, bitmapWidth.toFloat(), bodyHeight)
    canvas.drawRoundRect(bodyRect, 11f * density, 11f * density, paint)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    paint.color = groupColor
    canvas.drawRoundRect(
        android.graphics.RectF(
            paint.strokeWidth / 2f,
            paint.strokeWidth / 2f,
            bitmapWidth - paint.strokeWidth / 2f,
            bodyHeight - paint.strokeWidth / 2f
        ),
        11f * density,
        11f * density,
        paint
    )

    paint.style = android.graphics.Paint.Style.FILL
    paint.color = groupColor
    val pointer = android.graphics.Path().apply {
        moveTo(bitmapWidth / 2f - 6f * density, bodyHeight)
        lineTo(bitmapWidth / 2f, pointerTipY)
        lineTo(bitmapWidth / 2f + 6f * density, bodyHeight)
        close()
    }
    canvas.drawPath(pointer, paint)

    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 13f * density
    visibleNumbers.forEachIndexed { index, item ->
        val row = index / columns
        val column = index % columns
        val left = padding + column * (cell + gap)
        val top = padding + row * (cell + gap)
        val cx = left + cell / 2f
        val cy = top + cell / 2f
        paint.color = when {
            item.selected -> android.graphics.Color.rgb(22, 141, 226)
            item.isPending -> android.graphics.Color.rgb(117, 132, 153)
            else -> android.graphics.Color.rgb(227, 75, 10)
        }
        canvas.drawCircle(cx, cy, cell * 0.46f, paint)
        paint.color = android.graphics.Color.WHITE
        val textY = cy - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(item.number.toString(), cx, textY, paint)
    }

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// Tạo Drawable marker kiểu bong bóng bằng Canvas Android để hiển thị số thứ tự ở giữa.
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

internal fun createDriverMotorbikeBitmap(context: android.content.Context): android.graphics.Bitmap {
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
private data class MapOrderMarkerGroup(
    val key: String,
    val point: MapPoint,
    val markers: List<MapOrderMarker>
)

private const val MAP_MARKER_GROUP_PREFIX = "GHP_MAP_GROUP:"
private const val DRIVER_MARKER_TITLE = "🛵 Vị trí hiện tại của tài xế"

private fun mapMarkerGroupKey(marker: MapOrderMarker): String {
    // Chỉ gom các điểm có tọa độ thật. Đơn chưa có tọa độ đang tạm nằm tại GPS tài xế
    // phải giữ riêng để không tạo thành một cụm giả.
    if (!marker.hasRealCoordinate) return "pending:${marker.order.code}"
    return java.lang.String.format(
        java.util.Locale.US,
        "%.6f:%.6f",
        marker.point.latitude,
        marker.point.longitude
    )
}

private fun groupMapOrderMarkers(markers: List<MapOrderMarker>): List<MapOrderMarkerGroup> =
    markers.groupBy(::mapMarkerGroupKey).map { (key, grouped) ->
        MapOrderMarkerGroup(key = key, point = grouped.first().point, markers = grouped)
    }

internal fun goongStyle(context: android.content.Context): String =
    context.resources.openRawResource(com.example.giaohangpro.R.raw.goong_map_style)
        .bufferedReader().use { it.readText() }

@Composable
internal fun GoongOrderMap(
    modifier: Modifier,
    active: Boolean,
    orders: List<MapOrderMarker>,
    driverLocation: MapPoint?,
    selectedOrderNumber: Int?,
    expanded: Boolean,
    editingStt: Boolean,
    onToggleExpand: () -> Unit,
    onOrderSelected: (MapOrderMarker) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var didInitialDriverFocus by remember { mutableStateOf(false) }
    val driverIcon = remember(context) {
        org.maplibre.android.annotations.IconFactory.getInstance(context)
            .fromBitmap(createDriverMotorbikeBitmap(context))
    }
    val groupedOrderMarkers = remember(orders) { groupMapOrderMarkers(orders) }
    val currentGroupedOrderMarkers by rememberUpdatedState(groupedOrderMarkers)
    val currentSelectedOrderNumber by rememberUpdatedState(selectedOrderNumber)
    val currentOnOrderSelected by rememberUpdatedState(onOrderSelected)
    val currentActive by rememberUpdatedState(active)

    LaunchedEffect(active) {
        mapView.visibility = if (active) android.view.View.VISIBLE else android.view.View.INVISIBLE
        if (active) {
            mapView.onStart()
            mapView.onResume()
        } else {
            mapView.onPause()
            mapView.onStop()
        }
    }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (currentActive) mapView.onStart()
                Lifecycle.Event.ON_RESUME -> if (currentActive) mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> if (currentActive) mapView.onPause()
                Lifecycle.Event.ON_STOP -> if (currentActive) mapView.onStop()
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
                                if (!currentActive) return@setOnMarkerClickListener true
                                val groupKey = clicked.title
                                    ?.substringAfter(MAP_MARKER_GROUP_PREFIX, "")
                                    ?.trim()
                                    .orEmpty()
                                val group = currentGroupedOrderMarkers.firstOrNull { it.key == groupKey }
                                if (group != null) {
                                    // Một cụm có nhiều STT: mỗi lần chạm sẽ chọn STT kế tiếp.
                                    val choices = group.markers.distinctBy { it.number }.sortedBy { it.number }
                                    val selectedIndex = choices.indexOfFirst {
                                        it.number == currentSelectedOrderNumber
                                    }
                                    val marker = choices[
                                        if (selectedIndex >= 0) (selectedIndex + 1) % choices.size else 0
                                    ]
                                    currentOnOrderSelected(marker)
                                    true
                                } else false
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
                driverLocation?.let { point -> map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0)) }
            }
            MapControlButton(Icons.Default.Add, "Phóng to") { map?.animateCamera(CameraUpdateFactory.zoomBy(1.0)) }
            MapControlButton(Icons.Default.Remove, "Thu nhỏ") { map?.animateCamera(CameraUpdateFactory.zoomBy(-1.0)) }
            MapControlButton(if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Mở rộng bản đồ") { onToggleExpand() }
        }
    }

    LaunchedEffect(map, driverLocation) {
        val readyMap = map ?: return@LaunchedEffect
        val point = driverLocation ?: return@LaunchedEffect

        if (!didInitialDriverFocus) {
            readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
            didInitialDriverFocus = true
        }

        // GPS updates touch only the driver marker. Order markers stay intact.
        readyMap.markers.firstOrNull { it.title == DRIVER_MARKER_TITLE }?.let(readyMap::removeMarker)
        readyMap.addMarker(
            MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .icon(driverIcon)
                .title(DRIVER_MARKER_TITLE)
                .snippet("GPS đang cập nhật")
        )
    }

    // Rebuild order markers only when route/order marker data changes.
    // GPS updates and selection must not clear/recreate 100+ order markers.
    LaunchedEffect(map, groupedOrderMarkers, editingStt) {
        map?.let { readyMap ->
            readyMap.clear()
            driverLocation?.let { point ->
                readyMap.addMarker(
                    MarkerOptions().position(LatLng(point.latitude, point.longitude)).icon(driverIcon)
                        .title(DRIVER_MARKER_TITLE).snippet("GPS đang cập nhật")
                )
            }
            groupedOrderMarkers.forEach { group ->
                val numbers = group.markers.filter { it.showNumber }.map { it.number }.distinct().sorted()
                val numberBitmap = (
                    createGroupedNumberBubbleDrawable(context, group.markers, null)
                        as android.graphics.drawable.BitmapDrawable
                    ).bitmap
                val numberIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(numberBitmap)
                val firstMarker = group.markers.first()
                val numberText = numbers.joinToString(", ")
                val groupDescription = if (group.markers.size > 1) {
                    "${group.markers.size} đơn cùng điểm • Chạm lại để chọn STT kế tiếp"
                } else if (editingStt) {
                    "Chạm để đổi STT"
                } else if (firstMarker.hasRealCoordinate) {
                    firstMarker.order.address
                } else {
                    "Chưa có tọa độ giao hàng"
                }
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(group.point.latitude, group.point.longitude))
                        .icon(numberIcon)
                        .title("STT ${numberText.ifBlank { "—" }} • " + MAP_MARKER_GROUP_PREFIX + group.key)
                        .snippet(groupDescription)
                )
            }
        }
    }

    // Khi chọn đơn: giữ hiệu ứng marker xanh như trước nhưng chỉ cập nhật icon
    // của cụm cũ + cụm mới, không clear/rebuild toàn bộ 100+ marker.
    var previousSelectedNumber by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(map, selectedOrderNumber, groupedOrderMarkers) {
        val readyMap = map ?: return@LaunchedEffect
        val affectedNumbers = setOfNotNull(previousSelectedNumber, selectedOrderNumber)
        if (affectedNumbers.isNotEmpty()) {
            readyMap.markers
                .filter { marker ->
                    val key = marker.title
                        ?.substringAfter(MAP_MARKER_GROUP_PREFIX, "")
                        ?.trim()
                        .orEmpty()
                    val group = groupedOrderMarkers.firstOrNull { it.key == key }
                    group != null && group.markers.any { it.number in affectedNumbers }
                }
                .forEach { marker ->
                    val key = marker.title
                        ?.substringAfter(MAP_MARKER_GROUP_PREFIX, "")
                        ?.trim()
                        .orEmpty()
                    val group = groupedOrderMarkers.firstOrNull { it.key == key } ?: return@forEach
                    val bitmap = (
                        createGroupedNumberBubbleDrawable(context, group.markers, selectedOrderNumber)
                            as android.graphics.drawable.BitmapDrawable
                        ).bitmap
                    marker.icon = org.maplibre.android.annotations.IconFactory
                        .getInstance(context)
                        .fromBitmap(bitmap)
                }
        }
        // MapLibre v9 annotations không có zIndex ổn định cho Marker.
        // Đưa marker được chọn ra cuối collection để nó được vẽ trên các marker khác,
        // chỉ remove/add đúng 1 marker thay vì redraw toàn bản đồ.
        selectedOrderNumber?.let { number ->
            val selectedGroup = groupedOrderMarkers.firstOrNull { group ->
                group.markers.any { it.number == number }
            }
            if (selectedGroup != null) {
                val selectedMapMarker = readyMap.markers.firstOrNull { marker ->
                    marker.title
                        ?.substringAfter(MAP_MARKER_GROUP_PREFIX, "")
                        ?.trim() == selectedGroup.key
                }
                if (selectedMapMarker != null) {
                    val numbers = selectedGroup.markers.filter { it.showNumber }
                        .map { it.number }.distinct().sorted()
                    val bitmap = (
                        createGroupedNumberBubbleDrawable(context, selectedGroup.markers, number)
                            as android.graphics.drawable.BitmapDrawable
                        ).bitmap
                    readyMap.removeMarker(selectedMapMarker)
                    readyMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(selectedGroup.point.latitude, selectedGroup.point.longitude))
                            .icon(org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(bitmap))
                            .title("STT ${numbers.joinToString(", ").ifBlank { "—" }} • " + MAP_MARKER_GROUP_PREFIX + selectedGroup.key)
                            .snippet(selectedMapMarker.snippet)
                    )
                }
            }
        }
        previousSelectedNumber = selectedOrderNumber

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

@Composable
internal fun MapControlButton(
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
