# Quy chuẩn Git Workflow & CI/CD — Cruise Backend API

> **Dự án:** Cruise Activity and Service Management System - Backend API  
> **Jira Story:** [KAN-37](https://huytran20052022.atlassian.net/browse/KAN-37) | **Task:** [KAN-57](https://huytran20052022.atlassian.net/browse/KAN-57)

Tài liệu này định nghĩa quy chuẩn làm việc với Git, chiến lược phân nhánh (Branching Strategy), quy ước commit (Commit Conventions), quy trình Pull Request và hướng dẫn thiết lập bảo vệ nhánh trên GitHub.

---

## 1. Chiến lược phân nhánh (Branching Strategy: GitHub Flow)

Hệ thống áp dụng mô hình **GitHub Flow** tinh gọn, đảm bảo nhánh `main` luôn ở trạng thái sẵn sàng triển khai (Production-ready).

```
main (Protected) ──────────●────────────────────────● (Merge PR)
                           \                       /
feature/KAN-xx              ●────────●────────────● (Commits & PR)
```

### Các loại nhánh:
* **`main` (Nhánh chính):**
  * Luôn ổn định, phản ánh phiên bản triển khai mới nhất.
  * **Bảo vệ nghiêm ngặt:** Không được phép push trực tiếp (`git push origin main` bị cấm). Mọi thay đổi bắt buộc phải thông qua Pull Request (PR) được duyệt và vượt qua kiểm thử CI.
* **`feature/<JIRA-KEY>-<ten-tinh-nang>`:**
  * Sử dụng khi phát triển tính năng mới.
  * Ví dụ: `feature/KAN-57-configure-git-workflow-and-ci`, `feature/KAN-38-initialize-backend-project`.
* **`bugfix/<JIRA-KEY>-<ten-loi>`:**
  * Sử dụng khi sửa lỗi phát hiện trong quá trình kiểm thử hoặc phát triển.
  * Ví dụ: `bugfix/KAN-88-fix-jwt-expiration`.
* **`hotfix/<JIRA-KEY>-<ten-loi-khan-cap>`:**
  * Nhánh sửa lỗi khẩn cấp trực tiếp từ `main`.

---

## 2. Quy ước đặt tên Commit (Commit Message Conventions)

Cấu trúc commit tuân thủ chuẩn **Conventional Commits** kết hợp mã định danh Jira Task:

```
[<JIRA-KEY>] <type>: <Mô tả ngắn gọn hành động bằng tiếng Anh hoặc tiếng Việt>
```

### Danh sách các loại commit (`type`):
* `feat`: Thêm tính năng mới (Feature).
* `fix`: Sửa lỗi (Bug fix).
* `test`: Thêm hoặc chỉnh sửa test suite, cấu hình test.
* `refactor`: Tái cấu trúc mã nguồn (không thêm tính năng, không sửa lỗi).
* `docs`: Thêm hoặc cập nhật tài liệu (README, SPEC, docstring).
* `chore`: Cập nhật cấu hình build, dependency, scripts, CI/CD.
* `perf`: Cải thiện hiệu năng xử lý.

### Ví dụ chuẩn:
```bash
[KAN-57] feat: Configure Git workflow, branch protection rules, and GitHub Actions CI pipeline
[KAN-53] test: Add sample unit and mock tests for auth service
[KAN-41] feat: Configure PostgreSQL HikariCP connection pools
```

---

## 3. Quy trình làm việc hàng ngày (Daily Developer Workflow)

### Bước 1: Đồng bộ nhánh `main` mới nhất
```powershell
git checkout main
git pull origin main
```

### Bước 2: Tạo nhánh feature mới
```powershell
git checkout -b feature/KAN-xx-ten-tinh-nang
```

### Bước 3: Phát triển và kiểm thử cục bộ (Local Testing)
Trước khi commit, bắt buộc kiểm thử chạy thành công 100%:
```powershell
# Chạy test toàn bộ hoặc module liên quan
.\scripts\run-tests.ps1 -Module auth
```

### Bước 4: Commit và Push lên Remote
```powershell
git add .
git commit -m "[KAN-xx] feat: Mo ta thay doi"
git push -u origin feature/KAN-xx-ten-tinh-nang
```

### Bước 5: Mở Pull Request (PR) trên GitHub
1. Truy cập link Pull Request do terminal cung cấp hoặc trên giao diện GitHub.
2. Tiêu đề PR: `[KAN-xx] Tiêu đề tính năng`.
3. Gán Reviewer và gắn nhãn (Label).
4. Hệ thống **GitHub Actions CI Pipeline** sẽ tự động kích hoạt để kiểm tra code.
5. Sau khi CI pass (màu xanh ✅) và được duyệt, tiến hành bấm **Squash and merge** hoặc **Rebase and merge** vào `main`.
6. Xóa nhánh feature trên GitHub sau khi merge thành công.

---

## 4. Hướng dẫn thiết lập bảo vệ nhánh trên GitHub (Dành cho Repo Owner)

Để ngăn chặn việc vô tình ghi đè nhánh `main` và bắt buộc luồng CI hoạt động, Repository Owner cần thiết lập trên giao diện GitHub:

1. Truy cập repository: [Cruise-JavaCLass/Cruise-api](https://github.com/Cruise-JavaCLass/Cruise-api).
2. Vào **Settings** (tab trên cùng) ➔ chọn mục **Branches** ở cột bên trái.
3. Trong phần **Branch protection rules**, bấm nút **Add branch protection rule** (hoặc chọn *Rulesets*):
   * **Branch name pattern:** nhập `main`.
4. Tích chọn các mục sau:
   * ✅ **Require a pull request before merging:**
     * Bắt buộc mọi code vào `main` đều phải thông qua PR.
     * *(Tùy chọn)* Bật *Require approvals* (đặt giá trị: 1).
   * ✅ **Require status checks to pass before merging:**
     * Bật *Require branches to be up to date before merging*.
     * Trong ô tìm kiếm status checks, tìm và tích chọn: `Build & Test (Java 21 / Maven)`.
   * ✅ **Do not allow bypassing the above settings:** Áp dụng cho cả Administrators.
5. Bấm **Save changes** để hoàn tất.

---

## 5. Tự động hóa CI/CD với GitHub Actions

File cấu hình CI đặt tại: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

Mỗi khi có code mới được `push` hoặc `pull_request` vào `main`, pipeline tự động:
1. Khởi tạo môi trường Ubuntu với Java 21 Temurin.
2. Caching Maven dependencies để tăng tốc độ build.
3. Chạy lệnh `./mvnw -B verify` thực thi toàn bộ test của 7 microservices.
4. Tự động tải lên artifact:
   * **Báo cáo JaCoCo coverage:** lưu 14 ngày.
   * **Báo cáo kết quả kiểm thử Surefire XML:** lưu 7 ngày khi có lỗi.
5. **Kiểm tra SonarCloud Quality Gate:**
   * Quét toàn bộ mã nguồn, bugs, vulnerabilities, code smells.
   * Đọc báo cáo JaCoCo XML để tính toán code coverage.
   * Kiểm tra tiêu chí Quality Gate (`-Dsonar.qualitygate.wait=true`). Nếu không đạt, pipeline sẽ báo đỏ và khóa nút Merge PR.

---

## 6. Thiết lập SonarCloud Quality Gate

* **Tổ chức SonarCloud:** `cruise-javaclass`
* **Project Key:** `Cruise-JavaCLass_Cruise-api`
* **Secret yêu cầu trong GitHub Actions:** `SONAR_TOKEN` (trong *Settings > Secrets and variables > Actions*).
* **Tiêu chí Quality Gate mặc định (Sonar way):**
  * Coverage trên mã mới >= 80%.
  * Tỷ lệ trùng lặp mã <= 3%.
  * Xếp hạng Bảo mật (Security Rating): A.
  * Xếp hạng Độ tin cậy (Reliability Rating): A.
  * Xếp hạng Khả năng bảo trì (Maintainability Rating): A.
  * 100% Security Hotspots đã được đánh giá.
