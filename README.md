# Giao Hàng Pro - bản đồ OSM và Goong Geocoding

Bản này dùng tile **OpenStreetMap** qua OSMDroid để tab **Bản đồ** luôn hiển thị được mà không phụ thuộc API key tile.

## Bản đồ và Goong
- Tab Bản đồ dùng tile OpenStreetMap qua OSMDroid.
- Nút ghim trong form khách hàng mở bản đồ OSM để chạm chọn tọa độ.
- Nút **LẤY TỌA ĐỘ TỪ ĐỊA CHỈ** gọi Goong Geocoding để tự lấy Latitude/Longitude.
- Địa chỉ phụ cũng hỗ trợ lấy tọa độ bằng Goong.
- Marker đơn hàng vẫn giữ dạng bong bóng đánh số và đồng bộ với danh sách.
- Dẫn đường bên ngoài vẫn mở Google Maps tới Latitude/Longitude đã có.

## API key
`GOONG_API_KEY` được đặt trong `MainActivity.kt` cho Geocoding. Nếu key Goong chưa được cấp quyền, chức năng lấy tọa độ từ địa chỉ sẽ báo lỗi; bản đồ và chọn điểm bằng tay vẫn hoạt động.

Nên giới hạn key theo package/SHA-1 và quyền API/quota trên Goong Console.

## Dependency
```kotlin
implementation("org.osmdroid:osmdroid-android:6.1.20")
```

## Runtime Goong v3.04

Tab Bản đồ và màn hình chọn tọa độ dùng MapLibre với `res/raw/goong_map_style.json`, theo đúng runtime của app giaohang-goong-v3.04. Style vector tải các source Goong `base.json` và `goong.json` bằng key cấu hình trong style.
