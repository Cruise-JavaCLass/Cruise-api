# KAN-724–727: Đồng bộ POS ngoại tuyến

Nguồn yêu cầu: https://docs.google.com/document/d/1uDNJmpvD4PpiDNk5vLana-Ij04TnmaMd9pGTB5WNwKU

## Contract

Nằm trong `cruise-pos-service` (mặc định cổng 8084), sử dụng luồng charge, shift, folio và audit hiện có. Không thay endpoint batch cũ. Terminal phải đã đăng ký và ở trạng thái ACTIVE; station trên từng giao dịch phải khớp terminal. Header terminal chọn phạm vi của thiết bị đăng ký; nó không thay thế xác thực Bearer token hay cơ chế xác thực thiết bị khi triển khai.

| Method | Endpoint | Quyền |
| --- | --- | --- |
| POST | `/api/v1/pos/sync/batch` | POS_OPERATE |
| GET | `/api/v1/pos/sync/batch/{id}/status` | POS_READ; cần X-Terminal-Id khớp batch |
| GET | `/api/v1/pos/sync/conflicts` | FINANCE_MANAGE |
| POST | `/api/v1/pos/sync/conflicts/{id}/resolve` | FINANCE_MANAGE |
| POST | `/api/v1/pos/sync/heartbeat` | POS_OPERATE |

Mọi endpoint yêu cầu Bearer token theo RBAC hiện có. Endpoint batch cần `X-Terminal-Id` và `X-Batch-Id`. Mỗi request chứa 1–250 giao dịch. Mỗi giao dịch cần UUIDv7 `idempotencyKey`, `transactionReference` tối đa 64 ký tự và `transaction` theo CreateTransactionRequest hiện có. `transactionUuid` được lấy từ reference; nếu client gửi cả hai thì phải khớp.

```json
{
  "transactions": [{
    "idempotencyKey": "019a9f00-0000-7001-8000-000000000001",
    "transactionReference": "OFFLINE-00001",
    "transaction": {
      "stationId": "POS-DECK-9",
      "cashierId": "CASHIER-01",
      "cabinNumber": "CABIN-7012",
      "paymentMethod": "CABIN_FOLIO",
      "items": [{"itemCode":"WATER","itemName":"Water","category":"BEVERAGE","quantity":1,"unitPrice":10.00}]
    }
  }]
}
```

Đầu ra có kết quả từng record `SUCCESS`, `DUPLICATE` hoặc `ERROR`, `serverTransactionId`, `conflictId` và `errorCode`. Trả kèm số dư/hạn mức/trạng thái folio từ server để POS đối soát ngược về thiết bị. Phần tải danh mục đầy đủ tiếp tục dùng API catalog hiện có; đây không phải giao thức sao chép toàn bộ database client.

GET conflicts nhận `status=PENDING|APPROVED|REJECTED`, `page`, `size` (tối đa 100). Resolve dùng body `{"action":"APPROVE","reason":"..."}` hoặc `REJECT`; người duyệt lấy từ principal đã xác thực. Heartbeat dùng `X-Terminal-Id`, body `{"firmwareVersion":"1.2.3"}`.

## ACID, replay và conflict

- Cả batch nằm trong một transaction: claim key, charge cabin, POS transaction, outcome và batch status cùng commit. Lỗi database hoặc payload dùng lại key sai nội dung làm rollback toàn bộ batch, kể cả giao dịch đã đi qua trước đó.
- Lỗi nghiệp vụ dự kiến được ghi thành outcome ERROR; các record còn lại có thể SUCCESS trong cùng lần commit. CONFLICT_DETECTED nghĩa là xử lý batch đã hoàn tất nhưng còn quyết định thủ công.
- Cùng terminal dùng row lock, các shift được khóa trước folio theo cùng thứ tự với charge online; các folio được khóa theo số cabin đã sort để tránh deadlock giữa batch.
- Unique database trên key, reference và `(terminal_id,batch_number)` là lớp bảo vệ cuối. Request hash ràng buộc endpoint, terminal và nội dung. Không coi key trùng nhưng payload khác là thành công.
- Gửi lại nguyên batch sau mất phản hồi hoặc đóng gói cùng record trong batch mới trả DUPLICATE mà không debit lần hai. Canonical POS transaction và khóa idempotency online vẫn được sử dụng để không tạo một sổ tiền song song.
- Folio SETTLED/CLOSED tạo SyncConflictLog với trạng thái server/client và PENDING, không charge ngay. APPROVE của tài chính ghi giao dịch một lần, giữ trạng thái đóng ban đầu của folio và ghi audit; đây là khoản phát sinh được duyệt, không tự sửa hóa đơn quyết toán cũ. REJECT không đổi tiền. Quyết định lặp trả lại kết quả đã chốt.
- Khóa LOCKED/COMPLETED có expiresAt 30 ngày cho quản trị retention. Không tự xóa/reuse khóa tài chính khi hết hạn: replay cũ vẫn không được phép trừ tiền lại. Việc archive phải giữ tombstone/reference duy nhất lâu dài.
- Cập nhật phản hồi của conflict được duyệt vào các outcome liên quan để status/replay sau đó phản ánh quyết định mới.

## Kiểm tra

`OfflineSyncReplayTest` dùng service và repository thật trên H2: 10 giao dịch rồi replay, đóng gói lại, 20 yêu cầu replay đồng thời, mất phản hồi, rollback toàn batch, payload mismatch, UUID không đúng phiên bản, folio đóng, duyệt/từ chối thủ công, credit limit, heartbeat và terminal scope. Controller test kiểm tra thiếu header, thiếu token, body rỗng, giả vai trò và quyền tài chính. Migration PostgreSQL: `docs/db/KAN-724-offline-sync.sql`.

Không thay đổi database thật hoặc hệ thống terminal trong quá trình làm code. H2 không thay thế kiểm thử khóa/hiệu năng trên PostgreSQL production.

Kiểm tra chạy JAR với `pos.security.enabled=true` còn bao phủ luồng đăng nhập/introspection thật và duyệt conflict. `AuthPrincipal` triển khai `Principal.getName()` để audit ghi đúng username, thay vì chuỗi mô tả object bị vượt độ dài cột. `OfflineSyncSecurityIntegrationTest` chạy cả hai lớp filter, kiểm tra username đã xác thực và tiếp tục chặn cashier giả quyền tài chính.
