# Cruise Activity and Service Management System - Backend API

> **Jira Story:** [KAN-37](https://huytran20052022.atlassian.net/browse/KAN-37)  
> **Jira Tasks:**  
> - [KAN-38](https://huytran20052022.atlassian.net/browse/KAN-38) - *Initialize Backend Project — Khởi tạo dự án backend*  
> - [KAN-39](https://huytran20052022.atlassian.net/browse/KAN-39) - *Implement Basic Application Entry Point — Tạo điểm khởi chạy backend*  
> - [KAN-41](https://huytran20052022.atlassian.net/browse/KAN-41) - *Configure Database Connection Settings — Thiết lập thông tin kết nối cơ sở dữ liệu*  
> - [KAN-51](https://huytran20052022.atlassian.net/browse/KAN-51) - *Configure Backend Testing Tools — Thiết lập công cụ kiểm thử backend*  
> - [KAN-53](https://huytran20052022.atlassian.net/browse/KAN-53) - *Add Sample Backend Test — Tạo một test mẫu cho backend*  
> - [KAN-54](https://huytran20052022.atlassian.net/browse/KAN-54) - *Configure Test Execution Commands — Thiết lập lệnh chạy test*  
> - [KAN-57](https://huytran20052022.atlassian.net/browse/KAN-57) - *Configure Git Workflow and CI — Thiết lập quy trình Git và luồng CI*  
> - [KAN-60](https://huytran20052022.atlassian.net/browse/KAN-60) - *Implement Login and Logout Endpoints — Tạo API đăng nhập và đăng xuất*  
> - [KAN-61](https://huytran20052022.atlassian.net/browse/KAN-61) - *Implement Password Hashing and Verification — Xử lý mật khẩu an toàn*  
> - [KAN-62](https://huytran20052022.atlassian.net/browse/KAN-62) - *Implement Authentication Token Handling — Phát hành và xác minh token*  
> - [KAN-63](https://huytran20052022.atlassian.net/browse/KAN-63) - *Write Unit Tests for Authentication Logic — Test logic xác thực*  
> - [KAN-64](https://huytran20052022.atlassian.net/browse/KAN-64) - *Write API Tests for Login and Logout — Test API đăng nhập và đăng xuất*  
> - [KAN-65](https://huytran20052022.atlassian.net/browse/KAN-65) - *Test Invalid Credentials and Expired Tokens — Test thông tin sai và token hết hạn*  
> - [KAN-87](https://huytran20052022.atlassian.net/browse/KAN-87) - *Create Cruise Ship API — Tạo API quản lý tàu du lịch*  
> - [KAN-88](https://huytran20052022.atlassian.net/browse/KAN-88) - *Implement Ship Information Validation — Kiểm tra tính hợp lệ của thông tin tàu*  
> - [KAN-89](https://huytran20052022.atlassian.net/browse/KAN-89) - *Write Unit Tests for Ship Business Rules — Test các quy tắc nghiệp vụ tàu du lịch*  
> - [KAN-90](https://huytran20052022.atlassian.net/browse/KAN-90) - *Write API Tests for Ship CRUD Operations — Test các API CRUD tàu du lịch*  
> - [KAN-96](https://huytran20052022.atlassian.net/browse/KAN-96) - *Create and Update Cruise Tour API — Tạo và cập nhật API tour du lịch*  
> - [KAN-97](https://huytran20052022.atlassian.net/browse/KAN-97) - *Implement Tour Status Management — Quản lý trạng thái tour du lịch*  

---

## 1. Kiến trúc hệ thống (Microservices Architecture)

Dự án Backend được thiết kế theo mô hình **Multi-Module Maven Microservices**, bao gồm các dịch vụ chuyên biệt:

| Module | Mô tả | Port |
| :--- | :--- | :--- |
| **`cruise-common`** | Thư viện dùng chung (DTO, ApiResponse, ErrorCode, Enum Role, BaseEntity) | N/A (Library) |
| **`cruise-gateway`** | API Gateway điều hướng request cho Web Admin, Mobile App, POS Tablet | `8080` |
| **`cruise-auth-service`** | Quản lý tài khoản, phân quyền RBAC cho các phòng ban & hành khách | `8081` |
| **`cruise-itinerary-service`** | Quản lý tour du thuyền, lịch trình theo ngày, các cảng dừng (Ports of call) | `8082` |
| **`cruise-activity-service`** | Quản lý hoạt động giải trí trên tàu & tour trên bờ (Shore Excursions), check-in QR/RFID | `8083` |
| **`cruise-pos-service`** | Quản lý tài khoản phi tiền mặt, giao dịch POS, lưu trữ & đồng bộ ngoại tuyến (Offline Sync) | `8084` |

---

## 2. Yêu cầu môi trường
* **Java:** OpenJDK 21
* **Maven:** 3.9+ (Sử dụng sẵn script `mvnw` / `mvnw.cmd` không cần cài thêm)
* **Docker** *(tùy chọn)*: Để chạy Database PostgreSQL qua `docker-compose up -d`

---

## 3. Hướng dẫn biên dịch và khởi chạy

### Biên dịch toàn bộ các Microservices:
```powershell
.\mvnw compile
```

### Đóng gói file JAR:
```powershell
.\mvnw clean package
```

### Khởi chạy một Microservice (Ví dụ API Gateway):
```powershell
cd cruise-gateway
..\mvnw spring-boot:run
```

Hoặc trong **VS Code**:
* Mở tab **Spring Boot Dashboard** ở thanh sidebar bên trái.
* Nhấn nút **Play (Run/Debug)** vào service bạn muốn chạy.

---

## 4. Các Endpoint kiểm tra điểm khởi chạy (Gateway Entry Points)

Sau khi khởi chạy `cruise-gateway` (Port `8080`), các endpoint sẵn sàng phục vụ:

* **`GET /api/ping`**: Kiểm tra kết nối nhanh (trả về `"pong"`).
* **`GET /api/health`**: Kiểm tra trạng thái sức khỏe, thời gian hoạt động (uptime), dung lượng bộ nhớ JVM.
* **`GET /api/info`**: Thông tin metadata hệ thống, phiên bản Java, Jira Story & Task.
* **`GET /api/services`**: Bản đồ topology các Microservices đăng ký trong hệ sinh thái.

### 4.1. API Xác thực (Authentication Endpoints — Login & Logout):
Được cung cấp bởi `cruise-auth-service` (Port `8081`):

* **`POST /api/auth/login`**: Đăng nhập bằng `usernameOrEmail` và `password`.
  * **Request Body:**
    ```json
    {
      "usernameOrEmail": "admin",
      "password": "password123"
    }
    ```
  * **Response:** Trả về `200 OK` kèm `accessToken` (JWT), `tokenType` (`Bearer`), `expiresIn` (86400s) và thông tin đối tượng `user` (`role`, `cabinNumber`, `rfidTag`).
* **`POST /api/auth/logout`**: Đăng xuất an toàn.
  * Hỗ trợ nhận token qua Header `Authorization: Bearer <token>` hoặc Request Body `{"token": "<token>"}`.
  * Token được đưa vào danh sách thu hồi (Token Invalidation Cache) để ngăn chặn tái sử dụng.
* **`GET /api/auth/me`**: Lấy thông tin người dùng từ phiên đăng nhập hiện tại (`Authorization: Bearer <token>`).

### 4.2. Xử lý Mật khẩu An toàn (Password Hashing & Verification — KAN-61):
Hệ thống tích hợp giải pháp quản lý mật khẩu an toàn chuẩn OWASP:

* **Mã hóa băm (BCrypt Hashing):** Sử dụng `BCryptPasswordEncoder` với salt rounds chuẩn, lưu trữ mật khẩu an toàn trong cơ sở dữ liệu (`password_hash`), không bao giờ lưu trữ mật khẩu dưới dạng văn bản thuần (plain text).
* **Kiểm tra độ mạnh mật khẩu (`PasswordValidator`):**
  * Tối thiểu 8 ký tự, tối đa 100 ký tự.
  * Bắt buộc có: chữ in hoa (`A-Z`), chữ thường (`a-z`), số (`0-9`), ký tự đặc biệt (`!@#$%^&*...`).
  * Tự động từ chối danh sách mật khẩu yếu thông dụng (`password`, `12345678`, `admin123`,...).
* **Endpoints bổ sung:**
  * **`POST /api/auth/change-password`**: Đổi mật khẩu người dùng.
    * Yêu cầu Header `Authorization: Bearer <token>`.
    * Kiểm tra mật khẩu cũ, đảm bảo mật khẩu mới tuân thủ chính sách và không trùng mật khẩu cũ.
  * **`POST /api/auth/verify-password`**: Xác minh mật khẩu thô của tài khoản (dành cho các thao tác phê duyệt nhạy cảm hoặc thanh toán POS).

### 4.3. Quản lý và Xác minh Token (Authentication Token Handling — KAN-62):
Hệ thống cung cấp cơ chế toàn diện về phát hành, xác minh, làm mới và thu hồi Token chuẩn bảo mật doanh nghiệp:

* **Phát hành Cặp Token (Access Token & Refresh Token):**
  * **Access Token:** JWT ngắn hạn (mặc định 24h) chứa đầy đủ claims định danh (`sub`, `userId`, `email`, `fullName`, `role`, `cabinNumber`, `rfidTag`, `jti`, `iat`, `exp`, `iss`).
  * **Refresh Token:** JWT dài hạn (mặc định 7 ngày) dùng để cấp mới Access Token mà không cần đăng nhập lại.
* **Xác minh Token & Introspection (RFC 7662):**
  * **`POST /api/auth/verify-token`** (hoặc `/api/auth/validate-token`):
    * Hỗ trợ nhận token qua Header `Authorization: Bearer <token>` hoặc Request Body `{"token": "..."}`.
    * Kiểm tra chữ ký mật mã HMAC-SHA256, thời hạn hết hạn, và danh sách đen.
    * Trả về kết quả phân tích: `valid` (true/false), `status` (`VALID`, `EXPIRED`, `REVOKED`, `INVALID_SIGNATURE`, `MALFORMED`), thời gian còn lại `remainingSeconds`, thông tin user và claims.
* **Làm mới Token (Refresh Token Rotation):**
  * **`POST /api/auth/refresh-token`** (hoặc `/api/auth/refresh`):
    * Nhận `refreshToken`, xác minh tính hợp lệ và loại token `REFRESH`.
    * Áp dụng **Token Rotation**: Thu hồi refresh token cũ ngay lập tức để phòng chống Replay Attack và cấp phát cặp token mới.
* **Thu hồi Token (Token Revocation & Blacklist):**
  * **`POST /api/auth/revoke-token`** (hoặc `/api/auth/token/revoke`): Thu hồi tức thời một token.
  * **User-level Token Revocation:** Tự động vô hiệu hóa toàn bộ token cũ của người dùng khi đổi mật khẩu (`change-password`) hoặc reset phiên làm việc.
* **Cấp phát lại Token Pair:**
  * **`POST /api/auth/token/pair`**: Cấp mới cặp token cho phiên làm việc hiện tại (`Authorization: Bearer <token>`).

### 4.4. Quản lý Tàu Du Lịch (Cruise Ship Management Endpoints — KAN-87):
Được cung cấp bởi `cruise-itinerary-service` (Port `8082`):

Hệ thống quản lý toàn diện thông tin tàu du lịch, đặc tính kỹ thuật, sức chứa và trạng thái hoạt động:

* **`POST /api/itinerary/ships`**: Tạo mới tàu du lịch trong hệ thống.
  * **Status:** `201 Created` (hoặc `400 Bad Request`, `409 Conflict` khi trùng mã `code` hoặc `name`).
  * **Request Body:**
    ```json
    {
      "name": "Halong Grand Harmony",
      "code": "SHIP-001",
      "operator": "Paradise Vietnam Cruises",
      "capacity": 2400,
      "crewCapacity": 750,
      "totalCabins": 1100,
      "totalDecks": 14,
      "manufacturingYear": 2023,
      "lengthMeters": 315.5,
      "beamMeters": 38.0,
      "draftMeters": 8.5,
      "status": "ACTIVE",
      "description": "Du thuyền 5 sao đẳng cấp quốc tế",
      "amenities": "Sky Pool, Luxury Spa, Grand Ballroom",
      "imageUrl": "https://cruise.com/ships/ship-001.jpg"
    }
    ```
* **`GET /api/itinerary/ships`**: Lấy danh sách tàu du lịch với phân trang, tìm kiếm theo từ khóa và lọc theo trạng thái.
  * **Query Params:** `keyword`, `status` (`ACTIVE`, `DOCKED`, `ON_VOYAGE`, `UNDER_MAINTENANCE`, `INACTIVE`), `activeOnly` (mặc định `true`), `page`, `size`, `sortBy`, `direction`.
* **`GET /api/itinerary/ships/active`**: Lấy danh sách tất cả các tàu đang hoạt động (không phân trang, phục vụ dropdown chọn tàu).
* **`GET /api/itinerary/ships/{id}`**: Lấy chi tiết tàu theo ID (`200 OK` / `404 Not Found`).
* **`GET /api/itinerary/ships/code/{code}`**: Lấy chi tiết tàu theo mã hiệu `code` (`200 OK` / `404 Not Found`).
* **`PUT /api/itinerary/ships/{id}`**: Cập nhật thông tin chi tiết tàu (`200 OK` / `400 Bad Request` / `404 Not Found` / `409 Conflict`).
* **`PATCH /api/itinerary/ships/{id}/status`**: Cập nhật trạng thái tàu (`ACTIVE`, `DOCKED`, `ON_VOYAGE`, `UNDER_MAINTENANCE`, `INACTIVE`).
* **`DELETE /api/itinerary/ships/{id}`**: Hủy kích hoạt tàu (soft delete mặc định; thêm query `?hard=true` để xóa vĩnh viễn khỏi cơ sở dữ liệu).

* **Kiểm tra tính hợp lệ của thông tin tàu (`ShipValidator` — KAN-88):**
  * Định dạng mã hiệu tàu: 3-30 ký tự, chữ in hoa, số, gạch nối hoặc gạch dưới (`^[A-Z0-9_-]{3,30}$`).
  * Tương quan sức chứa & buồng phòng: Tổng số cabin không vượt quá sức chứa hành khách (`totalCabins <= capacity`).
  * Sức chứa thủy thủ đoàn: Tỷ lệ hợp lý không vượt quá gấp đôi sức chứa hành khách (`crewCapacity <= capacity * 2`).
  * Kích thước kỹ thuật: Chiều dài phải lớn hơn chiều rộng (`lengthMeters > beamMeters`) và mớn nước phải nhỏ hơn chiều rộng (`draftMeters < beamMeters`).
  * Năm sản xuất: Từ năm 1900 đến tối đa 5 năm trong tương lai (`1900 <= manufacturingYear <= currentYear + 5`).

* **Kiểm thử quy tắc nghiệp vụ tàu du lịch (`ShipBusinessRulesTest` — KAN-89):**
  * Kiểm thử phân tầng 5 nhóm quy tắc nghiệp vụ: Mã hiệu & tính duy nhất (Uniqueness), Sức chứa & tỷ lệ buồng phòng, Kích thước kỹ thuật hàng hải, Năm sản xuất & biên độ tương lai, Vòng đời & chuyển đổi trạng thái (Soft/Hard delete, đổi trạng thái tàu).

* **Kiểm thử API CRUD tàu du lịch (`CruiseShipControllerTest` & `CruiseShipIntegrationTest` — KAN-90):**
  * Kiểm thử toàn diện MockMvc Web Layer và Spring Boot Integration Test với cơ sở dữ liệu in-memory H2.
  * Bao phủ 100% các luồng thành công (200 OK, 201 Created) và các kịch bản lỗi biên: 400 Bad Request (vi phạm validation dữ liệu), 404 Not Found (ID/mã tàu không tồn tại), 409 Conflict (trùng lặp mã hoặc tên tàu khi tạo mới hoặc cập nhật).
  * Kiểm thử vòng đời trọn vẹn (Lifecycle), phân trang, lọc từ khóa, sắp xếp và cơ chế Soft Delete / Hard Delete.

### 4.5. Quản lý Tour Du Lịch Nhiều Ngày (Cruise Tour Management Endpoints — KAN-96):
Được cung cấp bởi `cruise-itinerary-service` (Port `8082`):

Hệ thống quản lý toàn diện các tour du thuyền nhiều ngày, gắn liền với tàu được chỉ định, lịch trình xuất bến/cập bến, sức chứa và chính sách giá:

* **`POST /api/itinerary/tours`**: Tạo mới tour du thuyền trong hệ thống.
  * **Status:** `201 Created` (hoặc `400 Bad Request`, `404 Not Found` nếu tàu không tồn tại, `409 Conflict` nếu trùng mã hoặc tên tour).
  * **Request Body:**
    ```json
    {
      "shipId": 1,
      "code": "TOUR-HL-3N2D",
      "name": "Hành trình Di sản Vịnh Hạ Long 3N2Đ",
      "description": "Tour khám phá vẻ đẹp hang Sửng Sốt và đảo Ti Tốp",
      "durationDays": 3,
      "durationNights": 2,
      "startDate": "2026-11-01",
      "endDate": "2026-11-03",
      "departurePort": "Cảng tàu khách quốc tế Hạ Long",
      "destinationPort": "Cảng tàu khách quốc tế Hạ Long",
      "maxCapacity": 1500,
      "basePrice": 5500000,
      "currency": "VND",
      "status": "DRAFT"
    }
    ```
* **`GET /api/itinerary/tours`**: Lấy danh sách tour với phân trang, lọc từ khóa, lọc theo tàu, lọc theo trạng thái (`DRAFT`, `PUBLISHED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`).
  * **Query Params:** `keyword`, `shipId`, `status`, `activeOnly` (mặc định `true`), `page`, `size`, `sortBy`, `direction`.
* **`GET /api/itinerary/tours/active`**: Lấy toàn bộ danh sách các tour đang kích hoạt (không phân trang).
* **`GET /api/itinerary/tours/{id}`**: Lấy chi tiết tour theo ID (`200 OK` / `404 Not Found`).
* **`GET /api/itinerary/tours/code/{code}`**: Lấy chi tiết tour theo mã định danh `code` (`200 OK` / `404 Not Found`).
* **`GET /api/itinerary/tours/{id}/status/allowed-transitions`**: Tra cứu danh sách các trạng thái hợp lệ có thể chuyển tiếp từ trạng thái hiện tại (`200 OK` / `404 Not Found`).
* **`GET /api/itinerary/tours/status/{status}`**: Lấy danh sách tour theo trạng thái cụ thể (`DRAFT`, `PUBLISHED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`).
* **`PUT /api/itinerary/tours/{id}`**: Cập nhật thông tin chi tiết tour (`200 OK` / `400 Bad Request` / `404 Not Found` / `409 Conflict`).
* **`PATCH /api/itinerary/tours/{id}/status`**: Chuyển trạng thái tour với kiểm tra quy tắc ma trận chuyển đổi (State Machine), kiểm tra tàu sẵn sàng và ghi nhận lý do hủy (`reason`).
* **`DELETE /api/itinerary/tours/{id}`**: Hủy kích hoạt tour (Soft Delete mặc định, tự động chuyển status sang `CANCELLED`; hoặc thêm `?hard=true` để xóa vĩnh viễn khỏi DB).

* **Quản lý vòng đời và ma trận chuyển đổi trạng thái Tour (`TourStatus` - KAN-97):**
  * `DRAFT` $\to$ `PUBLISHED`, `CANCELLED`
  * `PUBLISHED` $\to$ `IN_PROGRESS`, `DRAFT`, `CANCELLED`
  * `IN_PROGRESS` $\to$ `COMPLETED`, `CANCELLED`
  * `COMPLETED` $\to$ Trạng thái kết thúc (không thể chuyển đổi tiếp)
  * `CANCELLED` $\to$ `DRAFT` (cho phép khôi phục về bản nháp để điều chỉnh)
  * **Ràng buộc xuất bến:** Không cho phép chuyển tour sang `IN_PROGRESS` nếu tàu được chỉ định đang ở trạng thái ngừng hoạt động (`ship.active = false`).
  * **Ghi nhận lý do hủy:** Hỗ trợ lưu trữ `cancellationReason` khi tour bị hủy.

* **Kiểm tra tính hợp lệ của thông tin tour (`TourValidator`):**
  * Mã tour: Định dạng chữ hoa, chữ số, gạch nối hoặc gạch dưới từ 3-30 ký tự (`^[A-Z0-9_-]{3,30}$`).
  * Thời lượng tour: `durationDays >= 1`, `durationNights >= 0` và số đêm không vượt quá số ngày (`durationNights <= durationDays`).
  * Khung thời gian: `endDate` phải bằng hoặc sau `startDate`.
  * Sức chứa tương thích: Sức chứa tối đa của tour không được vượt quá sức chứa thiết kế của tàu phục vụ (`maxCapacity <= ship.capacity`).
  * Trạng thái tàu: Tàu phục vụ tour bắt buộc phải đang ở trạng thái hoạt động (`ship.active = true`).
  * Giá cơ bản: Giá tour cơ bản không được âm (`basePrice >= 0`).

* **Kiểm thử toàn diện Tour API (`TourValidatorTest`, `CruiseTourServiceTest`, `CruiseTourControllerTest`, `CruiseTourIntegrationTest`):**
  * Bao phủ 100% các tầng: Unit Validator, Service Mockito Unit Test, MockMvc Web Layer Test và Spring Boot Integration Test với H2 in-memory.
  * Kiểm tra đầy đủ lifecycle: Tạo tàu $\to$ Tạo tour $\to$ Tra cứu theo ID/Code $\to$ Tìm kiếm & phân trang $\to$ Cập nhật thông tin $\to$ Đổi trạng thái $\to$ Soft delete $\to$ Hard delete.

---

## 5. Cấu hình Cơ sở dữ liệu (Database Connection Settings)

Hệ thống hỗ trợ cả 2 môi trường: **PostgreSQL (Production/Default)** và **H2 In-Memory (Dev/Offline fallback)**:

### 5.1. PostgreSQL (Mặc định):
* **Host:** `localhost` | **Port:** `5432`
* **Database:** `cruise_db`
* **Username:** `cruise_admin` | **Password:** `cruise_password`
* **Connection Pool:** HikariCP (`maximum-pool-size: 10`, `minimum-idle: 2`)
* **Schemas:** Tự động tạo `auth_schema`, `itinerary_schema`, `activity_schema`, `pos_schema`.
* **Khởi chạy container PostgreSQL qua Docker Compose:**
  ```bash
  docker compose up -d
  ```

### 5.2. Chế độ Phát triển / Test Offline (H2 In-Memory):
Khi chưa bật Docker PostgreSQL, bạn có thể chạy service với profile `dev`:
```bash
.\mvnw -pl cruise-auth-service spring-boot:run -Dspring-boot.run.profiles=dev
```
Console H2 truy cập tại: `http://localhost:<port>/h2-console` (JDBC URL: `jdbc:h2:mem:cruise_<service>_db`)

---

## 6. Công cụ kiểm thử và Thiết lập lệnh chạy test (Test Execution Commands)

Hệ thống được thiết lập bộ công cụ kiểm thử chuẩn doanh nghiệp cho mô hình Spring Boot Microservices:

* **Testing Frameworks:**
  * **JUnit 5 (Jupiter):** Test runner, lifecycle callbacks, assertions.
  * **Spring Boot Test & WebMvc Test:** MockMvc, slice testing, ApplicationContext verification.
  * **AssertJ & Hamcrest:** Fluent assertions cho API payloads.
  * **Mockito:** Giả lập hành vi dependencies và service layer (`@MockitoBean`, `@Mock`, `@InjectMocks`).
* **Test Isolation Database:** H2 In-Memory Database cho test suites (chạy độc lập 100% không cần cài đặt hay bật PostgreSQL).
* **Code Coverage:** **JaCoCo Plugin** tự động tính toán tỷ lệ bao phủ mã nguồn và xuất báo cáo HTML.

---

### 6.1. Bảng lệnh chạy kiểm thử qua Maven Wrapper (`.\mvnw.cmd` / `./mvnw`):

| Lệnh thực thi | Mô tả chi tiết |
| :--- | :--- |
| `.\mvnw test` | Chạy toàn bộ test suites của tất cả các microservices |
| `.\mvnw test -pl cruise-auth-service` | Chạy test riêng cho một module (ví dụ `cruise-auth-service`, `cruise-gateway`,...) |
| `.\mvnw test -Dtest=UserRepositoryTest` | Chạy riêng một Test Class cụ thể |
| `.\mvnw test -Dtest=UserControllerTest#testRegisterUser_Success` | Chạy riêng một method test cụ thể |
| `.\mvnw test -Pquick` | Chế độ chạy test nhanh (bỏ qua agent JaCoCo để tăng tốc độ lặp khi dev) |
| `.\mvnw verify` | Đóng gói, chạy toàn bộ test và xuất báo cáo độ bao phủ JaCoCo HTML |

Báo cáo kiểm thử độ bao phủ (Coverage Report) được xuất tại:
`<module>/target/site/jacoco/index.html`

---

### 6.2. Script chạy test tự động (`scripts/run-tests`):
Dự án cung cấp sẵn bộ script tiện ích đa nền tảng cho lập trình viên:

#### Trên Windows (PowerShell):
```powershell
# Chạy toàn bộ test tất cả services
.\scripts\run-tests.ps1

# Chạy test riêng cho module auth
.\scripts\run-tests.ps1 -Module auth

# Chạy nhanh (bỏ qua JaCoCo)
.\scripts\run-tests.ps1 -Module auth -Quick

# Chạy test kèm xuất báo cáo coverage
.\scripts\run-tests.ps1 -Module auth -Coverage

# Chạy một test class cụ thể
.\scripts\run-tests.ps1 -Test UserRepositoryTest
```

#### Trên Linux / macOS / CI Runner (Bash):
```bash
# Cấp quyền thực thi và chạy
chmod +x ./scripts/run-tests.sh

# Chạy toàn bộ test
./scripts/run-tests.sh all

# Chạy module cụ thể
./scripts/run-tests.sh auth

# Chạy test class cụ thể
./scripts/run-tests.sh auth UserRepositoryTest
```

### 6.3. Cấu trúc Test mẫu (Sample Backend Tests):
Dự án cung cấp bộ test mẫu 3 tầng kiến trúc chuẩn (trong module `cruise-auth-service`):
1. **Repository Layer (`@DataJpaTest`):**
   - File: `UserRepositoryTest.java`
   - Kiểm thử thao tác cơ sở dữ liệu in-memory H2: CRUD, query methods (`findByUsername`, `findByRfidTag`, `findByRole`, `existsByUsername`), tự động ghi nhận auditing timestamps (`createdAt`, `updatedAt`).
2. **Service Layer (Unit Test with Mockito):**
   - File: `UserServiceTest.java`
   - Độc lập hoàn toàn với Spring Context và DB bằng `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`. Kiểm thử nghiệp vụ đăng ký người dùng, xử lý ngoại lệ khi username đã tồn tại, và tìm kiếm thông tin theo vai trò / RFID.
3. **Controller / Web Layer (`@WebMvcTest`):**
   - File: `UserControllerTest.java`
   - Kiểm thử tầng REST API qua `MockMvc` và `@MockitoBean`: endpoints `POST /api/auth/users` (201 Created, 400 Bad Request khi trùng lặp), `GET /api/auth/users/{username}` (200 OK, 404 Not Found).

### 6.4. Unit Test Suite cho Tầng Xác thực (Authentication Logic Unit Tests — KAN-63):
Module `cruise-auth-service` được trang bị bộ kiểm thử chuyên sâu (78/78 tests pass) bảo đảm logic xác thực an toàn tuyệt đối:
1. **`PasswordValidatorTest` (19 tests):**
   - Kiểm thử chính sách độ mạnh mật khẩu chuẩn OWASP: độ dài tối thiểu/tối đa, chữ in hoa, chữ thường, chữ số, ký tự đặc biệt.
   - Kiểm thử từ chối danh sách mật khẩu yếu thông dụng (`password`, `12345678`, `admin123`,...).
2. **`PasswordServiceTest` (5 tests):**
   - Kiểm thử băm BCrypt, so khớp mật khẩu và nhận diện định dạng hash BCrypt.
3. **`JwtServiceTest` (10 tests):**
   - Kiểm thử cấp phát Access Token, Refresh Token, Token Pair với đầy đủ claims định danh.
   - Kiểm thử xác minh chữ ký mã hóa HMAC-SHA256, xử lý token hết hạn, thu hồi token (Blacklist) và thu hồi toàn bộ token người dùng (User cutoff revocation).
4. **`AuthServiceTest` (17 tests):**
   - Kiểm thử toàn diện quy trình đăng nhập (username & email), kiểm tra tài khoản vô hiệu hóa, sai mật khẩu.
   - Kiểm thử làm mới token với cơ chế xoay vòng (Refresh Token Rotation), đổi mật khẩu an toàn, xác minh mật khẩu cho POS và lấy thông tin người dùng.
5. **`AuthControllerTest` (12 tests):**
   - Kiểm thử toàn bộ các endpoint RESTful qua `MockMvc` và `@MockitoBean`: `/login`, `/logout`, `/me`, `/refresh-token`, `/verify-token`, `/change-password`, `/verify-password`, `/token/pair`, `/health`.

### 6.5. Kiểm thử Thông tin sai & Token hết hạn (Invalid Credentials & Expired Tokens — KAN-65):
Bộ kiểm thử chuyên biệt tập trung vào các tình huống bảo mật biên, lỗi xác thực và phòng chống tấn công (Total 59 tests pass 100%):

1. **`InvalidCredentialsAndExpiredTokensIntegrationTest` (37 tests):**
   - **Invalid Credentials (18 tests):**
     - Đăng nhập sai mật khẩu với username / email hợp lệ trả về `401 Unauthorized` (`Invalid username or password`).
     - Đăng nhập với username / email không tồn tại trả về `401 Unauthorized`.
     - Đăng nhập tài khoản bị khóa / vô hiệu hóa (`active = false`) trả về `403 Forbidden`.
     - Đăng nhập tài khoản có mật khẩu null trong DB trả về `401 Unauthorized`.
     - Ngăn chặn tấn công SQL Injection (`' OR '1'='1`) và mã độc XSS vào username/password.
     - DTO Validation trả về `400 Bad Request` khi để trống username, password hoặc gửi body rỗng.
     - Xác minh mật khẩu (`/verify-password`) trả về `{ "valid": false }` khi sai mật khẩu hoặc user không tồn tại.
     - Đổi mật khẩu (`/change-password`) trả về `400 Bad Request` khi sai mật khẩu hiện tại, trùng mật khẩu cũ, xác nhận không khớp, hoặc mật khẩu mới yếu.
   - **Expired Tokens (7 tests):**
     - Xác minh Access Token / Refresh Token hết hạn qua body hoặc header `Authorization` trả về `valid: false` và `status: "EXPIRED"`.
     - Endpoint `/refresh-token` từ chối `401 Unauthorized` khi Refresh Token đã hết hạn.
     - Endpoint `/me`, `/change-password`, `/token/pair` từ chối `401 Unauthorized` khi phiên đăng nhập hết hạn.
   - **Tampered, Malformed & Revoked Tokens (12 tests):**
     - Phát hiện và từ chối token bị sửa chữ ký (`INVALID_SIGNATURE`) hoặc sửa payload giả mạo quyền hạn (`SUPER_HACKER`).
     - Từ chối token ký bởi khóa bí mật lạ hoặc chuỗi JWT dị dạng (`MALFORMED`).
     - Kiểm tra vô hiệu hóa token sau khi đăng xuất (`/logout`) và thu hồi tức thời (`/revoke-token`).
     - **Phòng chống Replay Attack:** Thu hồi Refresh Token cũ ngay sau khi làm mới (Token Rotation), từ chối `401 Unauthorized` khi token cũ bị sử dụng lại lần 2.
     - Ngăn chặn dùng Access Token cho endpoint `/refresh-token` và ngược lại.
     - **User Cutoff Revocation:** Tự động thu hồi toàn bộ token cũ đã cấp trước thời điểm đổi mật khẩu.
     - Kiểm tra từ chối `401 Unauthorized` khi thiếu hoặc định dạng sai Authorization Header.

2. **`InvalidCredentialsAndExpiredTokensUnitTest` (22 tests):**
   - Kiểm thử độc lập tầng Service (`AuthService`) và tầng mã hóa (`JwtService`).
   - Kiểm thử thuật toán băm BCrypt, so khớp, xử lý trường hợp user null/inactive, và ngoại lệ nghiệp vụ.
   - Kiểm thử cơ chế giải mã payload, tính toán timestamp `iat`, `exp`, `remainingSeconds`, phát hiện hết hạn và cô lập cache (`clearRevocations`).

### 6.6. Kiểm thử Tầng Quản lý Tàu Du Lịch (Cruise Ship Management Test Suite — KAN-87):
Module `cruise-itinerary-service` được trang bị bộ kiểm thử chuyên sâu (38/38 tests pass 100%) bao phủ toàn bộ các tầng kiến trúc:

1. **`CruiseShipRepositoryTest` (7 tests - DataJpaTest):**
   - Kiểm thử lưu trữ entity `CruiseShip`, tự sinh ID và audit timestamps (`createdAt`, `updatedAt`).
   - Kiểm thử tìm kiếm theo mã `code` và tên `name`.
   - Kiểm thử kiểm tra tồn tại `existsByCode`, `existsByName`, `existsByCodeAndIdNot`, `existsByNameAndIdNot`.
   - Kiểm thử lọc danh sách theo `status` và cờ `active`, đếm số lượng tàu `countByActiveTrue`.
   - Kiểm thử tìm kiếm nâng cao đa trường (`keyword`, `status`, `activeOnly`) kết hợp phân trang.

2. **`CruiseShipServiceTest` (16 tests - Mockito Unit Tests):**
   - Kiểm thử tạo mới tàu thành công với dữ liệu hợp lệ và chuẩn hóa (trimming, uppercase code).
   - Kiểm thử từ chối tạo mới khi trùng mã `code` hoặc trùng `name` (`DuplicateResourceException`).
   - Kiểm thử lấy thông tin tàu theo ID và mã hiệu `code`, xử lý ngoại lệ `ResourceNotFoundException`.
   - Kiểm thử tìm kiếm phân trang và danh sách tàu đang hoạt động (`active`).
   - Kiểm thử cập nhật thông tin tàu, kiểm tra không cho phép trùng `code`/`name` với tàu khác.
   - Kiểm thử cập nhật trạng thái hoạt động (`updateShipStatus`).
   - Kiểm thử xóa mềm (`active = false`, `status = INACTIVE`) và xóa cứng (`hardDelete`).
   - Kiểm thử thống kê số tàu đang hoạt động (`countActiveShips`).

3. **`CruiseShipControllerTest` (12 tests - WebMvcTest):**
   - Kiểm thử endpoint `POST /api/itinerary/ships`: tạo thành công `201 Created`, từ chối dữ liệu không hợp lệ `400 Bad Request`, từ chối trùng lặp `409 Conflict`.
   - Kiểm thử endpoint `GET /api/itinerary/ships` phân trang và lọc `200 OK`.
   - Kiểm thử endpoint `GET /api/itinerary/ships/active` lấy danh sách tàu hoạt động.
   - Kiểm thử endpoint `GET /api/itinerary/ships/{id}` (`200 OK`, `404 Not Found`).
   - Kiểm thử endpoint `GET /api/itinerary/ships/code/{code}` (`200 OK`).
   - Kiểm thử endpoint `PUT /api/itinerary/ships/{id}` (`200 OK`).
   - Kiểm thử endpoint `PATCH /api/itinerary/ships/{id}/status` (`200 OK`).
   - Kiểm thử endpoint `DELETE /api/itinerary/ships/{id}` (xóa mềm `200 OK` và xóa cứng `200 OK`).

4. **`CruiseShipIntegrationTest` (1 test - SpringBootTest E2E Lifecycle):**
   - Kiểm thử trọn vẹn vòng đời một tàu du lịch từ khởi tạo, tra cứu ID/mã hiệu, tìm kiếm bộ lọc, cập nhật thông tin, thay đổi trạng thái hải trình, xóa mềm và xóa cứng khỏi database.

---

## 7. Tài liệu đặc tả nghiệp vụ
Xem chi tiết toàn bộ đề bài và yêu cầu tại: [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md)

---

## 8. Quy trình Git Workflow & CI/CD Pipeline

Dự án áp dụng mô hình **GitHub Flow** và kiểm thử tự động qua **GitHub Actions**:
* **Chi tiết quy chuẩn:** Xem hướng dẫn đầy đủ tại [GIT_WORKFLOW.md](GIT_WORKFLOW.md).
* **Quy ước nhánh:** `feature/<JIRA-KEY>-<ten-tinh-nang>`, `bugfix/...`, `hotfix/...`.
* **Quy ước Commit:** `[<JIRA-KEY>] <type>: <description>` (theo chuẩn Conventional Commits).
* **Bảo vệ nhánh `main`:** Cấm push trực tiếp, bắt buộc tạo Pull Request và pass 100% CI checks trước khi merge.
* **GitHub Actions Pipeline (`.github/workflows/ci.yml`):** Tự động kích hoạt khi có Push hoặc PR vào `main`, khởi tạo Java 21, chạy `./mvnw -B verify`, lưu trữ báo cáo JaCoCo coverage report, và kiểm tra **SonarCloud Quality Gate** (`-Dsonar.qualitygate.wait=true`).
* **SonarCloud Quality Gate:** Tổ chức `cruise-javaclass`, Project `Cruise-JavaCLass_Cruise-api`, giám sát toàn diện Code Coverage (ngưỡng >= 80%), Bugs (0), Vulnerabilities (0), và Code Smells (Rating A).


