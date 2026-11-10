# Báo cáo kiểm thử: xuất kho đồng thời (Giai đoạn 5)

## Mục tiêu

Chứng minh hệ thống không bán quá số hàng đang có, kể cả khi nhiều phiếu xuất cùng tranh nhau số hàng cuối.

## Cách kiểm thử

Test nằm ở `OutboundApiTest` (hai test `concurrentOrdersOnQuantityItems` và `concurrentOrdersOnSerialItems`):

1. Nhập một lô có đúng 10 sản phẩm (một lần cho hàng theo số lượng, một lần cho hàng theo serial với 10 serial khác nhau).
2. Tạo 30 phiếu xuất, mỗi phiếu 1 sản phẩm (hằng số `ORDERS` trong test, có thể tăng lên 50 hoặc 100).
3. Gửi **cùng lúc** 30 yêu cầu nộp phiếu (giữ chỗ), mỗi yêu cầu một luồng.
4. Gửi cùng lúc các yêu cầu xác nhận xuất kho cho những phiếu đã giữ chỗ thành công.

## Kết quả mong đợi (test tự kiểm tra)

| Kiểm tra | Mong đợi |
|---|---|
| Số phiếu giữ chỗ thành công | đúng 10 |
| Số phiếu bị từ chối (409, hết hàng) | đúng 20 |
| Tồn thực tế sau khi nộp | 10 (giữ chỗ chưa làm giảm tồn thực tế) |
| Số đã giữ chỗ sau khi nộp | 10, không bao giờ vượt tồn |
| Xác nhận 10 phiếu đã giữ chỗ | cả 10 thành công |
| Tồn thực tế và số giữ chỗ cuối cùng | 0 và 0, không âm |
| Số dòng sổ giao dịch OUTBOUND | 10 |
| Hàng serial: số phân bổ và số serial khác nhau | cùng bằng 10 (không trùng serial) |

## Cơ chế đảm bảo

- Mỗi dòng tồn được khóa bằng `SELECT ... FOR UPDATE` (khóa bi quan) trước khi đọc số hàng còn lại.
- Các dòng tồn luôn được khóa theo cùng một thứ tự (lô nhận trước, rồi theo id) để tránh deadlock.
- Nếu MySQL vẫn hủy một transaction do deadlock, `Retry.onDeadlock` chạy lại tối đa 3 lần.
- Ràng buộc `CHECK` của MySQL là lớp bảo vệ cuối cùng: tồn thực tế không âm và số giữ chỗ không vượt tồn thực tế.

## Kết quả các lần chạy thực tế

Điền sau khi chạy `./mvnw test -Dtest=OutboundApiTest` nhiều lần liên tiếp (kế hoạch yêu cầu chạy xanh ổn định):

| Lần chạy | Ngày | Số phiếu (ORDERS) | Kết quả | Thời gian |
|---|---|---|---|---|
| 1 | | 30 | | |
| 2 | | 30 | | |
| 3 | | 30 | | |
