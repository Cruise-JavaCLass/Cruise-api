# KAN-708: Kiểm thử Dining Reservation

Nguồn chức năng có sẵn: nhánh `feat/dining-reservation-allergen-safety`, commit `5e405ba`. Test được viết trực tiếp cho service trong nhánh này rồi đồng bộ `main` hiện tại; không tạo một bản DiningReservationService khác.

- Unit test dùng JUnit 5, `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`.
- Bao phủ đặt bàn thành công, hết bàn, trùng giờ của cùng hành khách, hủy và kiểm tra chủ booking, booking không tồn tại, danh sách cá nhân, thiếu hồ sơ dị ứng và chi tiết cảnh báo.
- Allergen test kiểm tra toàn bộ enum, mức CRITICAL, MILD, nguyên liệu không liên quan, nguyên liệu null trong danh sách, và supervisor override.
- Integration test dùng JPA thật: hai khách tranh cùng một bàn chỉ một người thành công; hủy làm bàn xuất hiện lại trong truy vấn availability; một khách không thể giữ hai bàn cùng slot.
- Bổ sung khóa passenger rồi restaurant trong transaction đặt bàn: kiểm tra còn bàn và ghi booking được bảo vệ trước request đồng thời.

Chạy:

```text
mvn -B -pl cruise-activity-service -am verify
```

Coverage đo riêng package `com.cruise.activity.dining.service` trong JaCoCo; bài chạy mục tiêu ban đầu đạt 81/81 dòng và 16/16 nhánh (100%), vượt yêu cầu 85%. Báo cáo cuối đi kèm bản bàn giao. Kiểm thử không đánh giá tính đúng đắn y khoa của thuật toán dị ứng dựa trên chuỗi nguyên liệu hiện có.
