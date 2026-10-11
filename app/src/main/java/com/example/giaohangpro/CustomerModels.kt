package com.example.giaohangpro

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


data class CustomerBackupResult(
    val total: Int,
    val added: Int,
    val updated: Int,
    val skipped: Int = 0,
    val images: Int = 0,
    val missingImages: Int = 0
)


internal fun createInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
