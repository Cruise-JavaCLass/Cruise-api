# Cruise Activity and Service Management System - Backend API Specification

Vui lòng xem chi tiết tài liệu yêu cầu và đề bài đầy đủ tại:
[PROJECT_SPECIFICATION.md](file:///c:/Users/huytr/Desktop/JavaLesson/docs/PROJECT_SPECIFICATION.md)

### Tổng quan chức năng Backend:
1. **Quản lý Hải trình (Itinerary Management):** Tours, Ports of Call, Daily Schedule.
2. **Quản lý Hoạt động & Dịch vụ (Onboard Activities & Services):** Đăng ký trước/trong chuyến đi, giới hạn sức chứa, phí tham gia.
3. **Quản lý Tour trên bờ (Shore Excursions):** Lịch trình, đối tác tour địa phương, danh sách đoàn, quản lý giờ tập trung và quay lại tàu.
4. **Tài khoản phi tiền mặt (Cashless Onboard Account):** Quản lý tài khoản theo khách/cabin, ghi nợ, chi tiêu tạm tính.
5. **Giao dịch POS & Đồng bộ Offline (POS Transactions & Offline Sync):** Tiếp nhận giao dịch từ thiết bị POS, chống trùng lặp (idempotency key), giải quyết xung đột khi sync.
6. **Xác thực & Check-in:** QR Code, Thẻ du thuyền (Cruise Card), RFID/NFC Wristband.
7. **Đối soát & Quyết toán (Reconciliation & Settlement):** Xử lý tranh chấp, xuất hóa đơn cuối chuyến đi.
8. **Báo cáo & Giám sát vận hành (Reporting & Operations Monitoring):** Thống kê công suất, cảnh báo khách chưa về tàu, doanh thu.
9. **Tùy chọn: Định vị BLE (Zone-Level Passenger Location):** Cập nhật vùng vị trí của khách hỗ trợ phục vụ và an toàn khẩn cấp.
