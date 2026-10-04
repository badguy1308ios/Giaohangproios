package com.example.giaohangpro

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

// Lấy vị trí hiện tại bằng Android LocationManager, không cần Google Play Services hoặc API key.
@Composable
internal fun rememberDriverLocation(active: Boolean = true): State<MapPoint?> {
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
    LaunchedEffect(active) {
        if (active && !hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Đăng ký cập nhật vị trí chỉ khi composable đang tồn tại và quyền đã được cấp.
    DisposableEffect(context, hasPermission, active) {
        if (!hasPermission || !active) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager

        // Không cho một bản tin NETWORK kém chính xác kéo marker khỏi GPS tốt vừa nhận.
        // Android có thể phát callback từ nhiều provider xen kẽ nên phải chọn "best fix",
        // thay vì nhận callback nào cuối cùng thì dùng callback đó.
        var bestLocation: android.location.Location? = null
        fun acceptLocation(candidate: android.location.Location) {
            if (candidate.latitude !in -90.0..90.0 || candidate.longitude !in -180.0..180.0) return
            val current = bestLocation
            val shouldUse = when {
                current == null -> true
                candidate.time > current.time + 120_000L -> true
                candidate.time + 120_000L < current.time -> false
                candidate.hasAccuracy() && current.hasAccuracy() ->
                    candidate.accuracy <= current.accuracy + 25f
                candidate.hasAccuracy() && !current.hasAccuracy() -> true
                else -> candidate.time >= current.time
            }
            if (shouldUse) {
                val previous = bestLocation
                bestLocation = candidate

                // LocationManager can emit GPS/NETWORK/PASSIVE callbacks many times per second.
                // Do not wake Compose/MapLibre for tiny/no-op movements.
                val movedEnough = previous == null || previous.distanceTo(candidate) >= 5f
                val staleEnough = previous == null || candidate.time - previous.time >= 5_000L
                if (movedEnough || staleEnough) {
                    locationState.value = MapPoint(candidate.latitude, candidate.longitude)
                }
            }
        }

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                acceptLocation(location)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Suppress("DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }

        try {
            // PASSIVE_PROVIDER giúp nhận luôn vị trí mới mà các app/dịch vụ hệ thống
            // (ví dụ Google Maps) vừa xác định, rất hữu ích trên máy OEM khi GPS direct chậm.
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).distinct().filter { provider ->
                provider == LocationManager.PASSIVE_PROVIDER ||
                    runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }

            // Nạp cache theo chất lượng + độ mới, không để cache provider đầu tiên thắng cố định.
            providers.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.sortedBy { it.time }.forEach(::acceptLocation)

            // Xin cập nhật ngay. Nếu một provider không khả dụng thì provider khác vẫn tiếp tục.
            providers.forEach { provider ->
                runCatching {
                    manager.requestLocationUpdates(provider, 5_000L, 10f, listener, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) {
            // Quyền có thể bị thu hồi trong lúc chạy; giữ bản đồ hoạt động thay vì crash.
        }
        onDispose {
            runCatching { manager.removeUpdates(listener) }
        }
    }
    return locationState // Trả State để nơi gọi đọc bằng .value.
}
