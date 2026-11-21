# KAN-709–713: BLE telemetry và đối soát muster

Nguồn yêu cầu: https://docs.google.com/document/d/1uDNJmpvD4PpiDNk5vLana-Ij04TnmaMd9pGTB5WNwKU

## Phạm vi

Chức năng nằm trong `cruise-activity-service`, dùng chung bảng sự cố/check-in SOLAS hiện có. Bổ sung DeckZone, BleBeaconDevice, PassengerLocationLog, MusterStation, phân công trạm và roster chụp tại thời điểm bắt đầu. Roster lấy **hành khách active đã BOARDED của tour được chọn**, không lấy toàn bộ hành khách của các tour khác.

Đây là triển khai theo yêu cầu học thuật của dự án; không phải chứng nhận đáp ứng quy định SOLAS hay thay thế quy trình cứu sinh thực tế.

## API

Các endpoint `/api/v1/safety/**` yêu cầu Bearer token có quyền `SAFETY_MANAGE`: `OPERATIONS_MANAGER`, `SAFETY_OFFICER`, hoặc `SYSTEM_ADMIN` theo quyền quản trị sẵn có. Role mới chỉ được cấp quyền an toàn, không có quyền quản lý người dùng hay tài chính. Actor lấy từ danh tính đã xác thực, không tin header tự khai báo vai trò.

| Method | Endpoint | Nội dung |
| --- | --- | --- |
| POST | `/api/v1/safety/config/stations` | Cấu hình trạm, trưởng trạm, xuồng và capacity |
| POST | `/api/v1/safety/config/zones` | Cấu hình zone và boong 1–14 |
| POST | `/api/v1/safety/config/beacons` | Gán MAC beacon cho một hành khách active |
| POST | `/api/v1/safety/config/assignments` | Phân công hành khách vào trạm |
| POST | `/api/v1/safety/telemetry/pings` | Tiếp nhận ping; 202 là đã nhận vào bộ đệm, chưa phải đã commit DB |
| GET | `/api/v1/safety/telemetry/passengers/{id}` | Vị trí cuối cùng, timestamp và `fresh` |
| POST | `/api/v1/safety/muster/drill/start` | Tạo sự cố và roster theo tour |
| POST | `/api/v1/safety/muster/checkin` | Ghi nhận RFID/NFC/BLE/MANUAL và đối chiếu trạm |
| GET | `/api/v1/safety/muster/live-dashboard?drillId=1` | Tổng, đã đến, còn thiếu, tỷ lệ và thống kê từng trạm |
| GET | `/api/v1/safety/muster/missing-passengers?drillId=1` | Khách chưa đến đúng trạm và vị trí cuối |
| POST | `/api/v1/safety/muster/drill/{id}/close` | ALL_CLEAR và kết thúc phiên |

Dashboard và missing list nhận `stationCode`. Missing list nhận `status=MISSING`, `WRONG_MUSTER_STATION`, hoặc `ARRIVED`; mặc định trả toàn bộ khách chưa đến đúng trạm. `drillId` bắt buộc để không ghi nhầm vào phiên khác. API nằm trực tiếp ở activity-service (mặc định cổng 8083); gateway/reverse proxy cần định tuyến đường dẫn này khi triển khai.

Ví dụ start:

```json
{"drillType":"DRILL_PRACTICE","tourId":7}
```

Ví dụ check-in:

```json
{"drillId":1,"stationCode":"STATION-A","passengerId":101,"method":"NFC"}
```

Ví dụ telemetry:

```json
{"macAddress":"AA:BB:CC:DD:EE:FF","zoneCode":"DECK-09-LIDO-POOL","rssi":-58,"timestamp":"2026-11-21T07:00:00Z"}
```

Thiết bị/gateway thực hiện đọc BLE, RFID/NFC rồi gửi dữ liệu; backend không trực tiếp đọc phần cứng. Ping vị trí không tự chứng minh có mặt tại trạm: gateway dùng check-in với method BLE khi xác nhận hành khách đã đến trạm. Có mặt cũng không tự xác nhận áo phao đã được kiểm tra.

## Nhất quán và tải

- Chụp phân công vào roster để thay đổi phân công sau đó không sửa lịch sử phiên đang chạy.
- Khách sai trạm nhận `WRONG_MUSTER_STATION` và vẫn thuộc danh sách thiếu. Đến đúng trạm sau đó được ghi nhận. Quét lặp không tạo thêm check-in.
- Khóa dòng incident khi check-in, unique `(incident_id, passenger_id)` và unique active tour bảo vệ khỏi race ở database. Close dùng chính luồng ALL_CLEAR hiện có.
- Vị trí hiện tại dùng ConcurrentHashMap, TTL mặc định 2 phút. Vị trí hết TTL vẫn được báo rõ `fresh=false` để đội tìm kiếm phân biệt dữ liệu cũ. Ping cũ không đè vị trí mới, timestamp quá tương lai bị từ chối.
- Bộ đệm mặc định 20.000 ping; đầy trả 429, không âm thầm nhận rồi bỏ. Flush tối đa 1.000 mẫu/lần mỗi 1 giây qua `@Async` và executor giới hạn. Batch lỗi được giữ lại và thử lần kế tiếp.
- Lịch sử và heartbeat ghi chung một transaction. Truy vấn latest theo `(passenger_id, recorded_at DESC, id DESC)` và missing list lấy vị trí theo tập ID để tránh một query cho mỗi khách.
- Bộ đệm và live cache là **in-memory cho một instance**. Dừng đột ngột có thể mất mẫu chưa flush; nhiều instance cần Redis/broker hoặc tầng tiếp nhận bền vững. Không có cam kết delivery bền vững cho HTTP 202 trong cấu hình này.

Cấu hình:

```properties
cruise.safety.telemetry.ttl=PT2M
cruise.safety.telemetry.buffer-capacity=20000
cruise.safety.telemetry.flush-delay-ms=1000
cruise.safety.telemetry.scheduling-enabled=true
```

## Schema và kiểm tra

Migration PostgreSQL: `docs/db/KAN-709-safety-telemetry.sql`. Apply vào schema activity-service sau schema hiện có; môi trường phát triển `ddl-auto=update` cũng đọc mapping JPA. Không chạy migration tự động trên database của người dùng trong đợt triển khai code này.

Tests gồm quyền của toàn bộ vai trò, chống header giả, kiểm tra body, cấu hình, cache/TTL, batch retry, đúng/sai trạm, vị trí cuối, đóng phiên, 1.000 yêu cầu check-in đồng thời với 200 khách, và tải 5.000 ping. Benchmark kiểm tra ngưỡng 500 ping/giây và trung vị đối soát dưới 50 ms trên H2 cục bộ; kết quả là bằng chứng ở môi trường thử, không phải SLA production.
