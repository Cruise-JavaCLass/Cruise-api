# Cruise Activity and Service Management System - Backend API

> **Jira Story:** [KAN-37](https://huytran20052022.atlassian.net/browse/KAN-37)  
> **Jira Tasks:**  
> - [KAN-38](https://huytran20052022.atlassian.net/browse/KAN-38) - *Initialize Backend Project — Khởi tạo dự án backend*  
> - [KAN-39](https://huytran20052022.atlassian.net/browse/KAN-39) - *Implement Basic Application Entry Point — Tạo điểm khởi chạy backend*  

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

---

## 5. Tài liệu đặc tả nghiệp vụ
Xem chi tiết toàn bộ đề bài và yêu cầu tại: [PROJECT_SPECIFICATION.md](PROJECT_SPECIFICATION.md)

