# Sửa ảnh cổng nhà và lỗi mở Sửa khách hàng

Hải báo chỉ một số khách hàng bị khung ảnh trắng và bấm Sửa thì văng app.
Video `1000093855.mp4` thể hiện đường đi Chi tiết đơn -> Chi tiết khách hàng -> thoát app.
Chưa có stack trace trên điện thoại để khẳng định nguyên nhân crash.

Code cũ giải mã ảnh nguyên độ phân giải trên UI thread ở Chi tiết; form Sửa dùng
ImageView.setImageURI cũng tải ảnh nguyên kích thước. Lỗi đọc ảnh bị bỏ qua, để khung trắng.

Thay đổi:
- Hai màn hình dùng chung bộ tải ảnh trên Dispatchers.IO.
- Đọc kích thước trước và lấy mẫu ảnh, cạnh dài tối đa khoảng 1280 pixel.
- Hỗ trợ content URI, file URI và đường dẫn file thuần.
- Không đọc được ảnh thì hiện thông báo; người dùng có thể chọn/chụp lại ảnh.
- Kết nối thao tác chạm ảnh ở Chi tiết với menu ảnh đã có sẵn.
- Chỉ giảm kích thước ảnh xem trước, không sửa/xóa ảnh gốc hay dữ liệu khách hàng.

Kiểm tra: unit tests kích thước ảnh camera ngang/dọc, ảnh nhỏ, biên 1281 pixel,
kích thước không hợp lệ và kích thước rất lớn; CI testDebugUnitTest + assembleDebug.
Các test này không thay thế kiểm tra decoder, quyền URI và crash trên điện thoại.

Hải thử đúng khách bị lỗi trong video: mở ảnh, bấm Sửa, Hủy và mở lại;
sau đó thử khách đang hoạt động tốt. Nếu ảnh đã mất hoặc không còn quyền đọc,
bộ tải ảnh không thể phục hồi nội dung: cần chọn lại ảnh hoặc khôi phục bản sao có ảnh.
Nếu vẫn crash, cần log lỗi runtime để xác định nguyên nhân còn lại.
