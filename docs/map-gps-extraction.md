# Bước 1: tách Map/GPS

## Mốc khôi phục

- Build #28: `95a8f69763a9cf169ee5447f6773d7862c60499f` (mốc Hải yêu cầu giữ).
- Trước bước 1: Build #29, `0007132a9281479cecaa76e646e9872dc9c838a5`.
- Nếu có regression, khôi phục bằng revert commit tách Map/GPS, giữ lịch sử Git.

## Phạm vi

- `MapScreen.kt`: màn hình bản đồ, danh sách dưới bản đồ, tạo tuyến và sửa STT.
- `GoongMap.kt`: MapLibre, vòng đời MapView, bitmap/nhóm marker và nút bản đồ.
- `DriverLocation.kt`: quyền vị trí, LocationManager, đăng ký/hủy cập nhật GPS.
- `CustomerCoordinateMapPicker.kt`: bản đồ chọn tọa độ, giữ nguyên màn hình gọi.
- `MapCoordinates.kt`: đọc tọa độ, khoảng cách và mở Google Maps.
- `MapModels.kt`: MapPoint và MapOrderMarker.

Giữ nguyên thân hàm, khóa remember/effect, listener, tần suất GPS và callback.
Các helper dùng chung đổi private thành internal để truy cập qua ranh giới file.
Orders, Customers, dữ liệu lưu và MainViewModel chưa tách ở bước này.
Không sửa tools/, dependency, giao diện hoặc quy tắc STT.

## Kiểm tra

- So sánh toàn bộ dòng mã thực thi trước/sau, bỏ qua import, comment và mức truy cập.
- GitHub Actions chạy `gradle testDebugUnitTest assembleDebug`.
- Sau khi build thành công, chờ Hải xác nhận kiểm tra trên điện thoại trước bước 2.

## Hải kiểm tra trên điện thoại

1. Mở bản đồ, chuyển qua lại ba tab; chạm ở tab chi tiết đơn không làm STT nhảy về 1.
2. GPS tài xế, nút vị trí của tôi, thu/phóng và mở rộng bản đồ; xuống nền rồi mở lại.
3. Các đơn cùng tọa độ hiện đủ STT; chọn marker và focus đúng đơn.
4. Tạo tuyến, sửa STT ở danh sách, lưu, nhập/xuất và xóa STT.
5. Đã giao/Giao lại đồng bộ STT, thứ tự và marker như bản trước.
6. Chọn/lưu/hủy tọa độ ở khách hàng và cài đặt; dẫn đường Google Maps.

Build thành công không thay thế kiểm tra GPS và tương tác thực tế trên thiết bị.
