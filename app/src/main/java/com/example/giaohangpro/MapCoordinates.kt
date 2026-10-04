package com.example.giaohangpro

import android.widget.Toast

internal val DEFAULT_MAP_POINT = MapPoint(17.4689, 106.6220) // Đồng Hới, Quảng Bình.

// Chuyển text Latitude/Longitude thành MapPoint an toàn; dữ liệu sai sẽ trả null.
internal fun pointFromStrings(latitude: String, longitude: String): MapPoint? {
    val lat = latitude.trim().toDoubleOrNull() ?: return null // Không phải số thì không tạo marker.
    val lng = longitude.trim().toDoubleOrNull() ?: return null // Không phải số thì không tạo marker.
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null // Kiểm tra phạm vi tọa độ hợp lệ.
    return MapPoint(lat, lng) // Trả về điểm hợp lệ.
}

internal fun straightDistanceMeters(a: MapPoint, b: MapPoint): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(a.latitude)
    val p2 = Math.toRadians(b.latitude)
    val dp = Math.toRadians(b.latitude - a.latitude)
    val dl = Math.toRadians(b.longitude - a.longitude)
    val h = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) + kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
}

internal fun openGoogleNavigation(context: android.content.Context, point: MapPoint) {
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
