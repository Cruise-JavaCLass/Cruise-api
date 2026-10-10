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
* **GitHub Actions Pipeline (`.github/workflows/ci.yml`):** Tự động kích hoạt khi có Push hoặc PR vào `main`, khởi tạo Java 21, chạy `./mvnw -B verify`, và lưu trữ báo cáo JaCoCo coverage report.


