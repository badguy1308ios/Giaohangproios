package com.example.giaohangpro

import androidx.compose.ui.graphics.Color

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
internal val AllowedOrderStatuses = setOf("TT500", "TT506", "TT507", "TT508", "TT505", "TT515")
private val TerminalOrderStatuses = setOf("TT505", "TT515")
internal val HighlightServiceCodes = setOf("GGDH", "GG1P", "PTTX", "GBP")

internal fun normalizeOrderStatus(raw: String): String {
    val clean = raw.trim().uppercase()
    return when {
        clean in AllowedOrderStatuses -> clean
        else -> "TT500"
    }
}

internal fun isTerminalOrderStatus(status: String): Boolean =
    normalizeOrderStatus(status) in TerminalOrderStatuses

internal fun orderStatusColor(status: String): Color = when (normalizeOrderStatus(status)) {
    "TT500" -> Color(0xFF1976D2)
    "TT506", "TT507", "TT508" -> Color(0xFFF57C00)
    "TT505", "TT515" -> Color(0xFFD32F2F)
    else -> Color(0xFF1976D2)
}
