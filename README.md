# Electronics Warehouse System

Hệ thống quản lý kho thiết bị điện tử cho doanh nghiệp B2B — Backend Java (Spring Boot 4 + MySQL 8.4).

> **Trạng thái:** Đang phát triển | Giai đoạn 1–7 hoàn thành (backend) | Giai đoạn 8–9 sắp tới

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.4-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)
![GitHub Actions](https://img.shields.io/badge/CI-GitHub%20Actions-green)

## Mục lục

- [Giới thiệu](#giới-thiệu)
- [Kiến trúc](#kiến-trúc)
- [Công nghệ](#công-nghệ)
- [Tiến độ theo giai đoạn](#tiến-độ-theo-giai-đoạn)
- [API chính](#api-chính)
- [Kỹ thuật nổi bật](#kỹ-thức-nổi-bật)
- [Cách chạy dự án](#cách-chạy-dự-án)
- [Kiểm thử](#kiểm-thử)
- [Quy ước Git](#quy-ước-git)
- [Tài liệu](#tài-liệu)

## Giới thiệu

Dự án tập trung vào **tính đúng đắn của dữ liệu tồn kho**, không chỉ là CRUD đơn giản. Mọi thay đổi tồn nằm trong một transaction và có dòng trong sổ giao dịch bất biến. Chống xuất vượt tồn khi nhiều người thao tác cùng lúc bằng pessimistic lock, optimistic lock và ràng buộc CHECK ở database.

## Kiến trúc

**Modular monolith:** Một ứng dụng Spring Boot chia thành các module theo nghiệp vụ. Mỗi module có 5 package: `controller`, `service`, `repository`, `entity`, `dto`. Phần dùng chung nằm trong `common`.

Lý do không dùng microservices: nghiệp vụ kho cần cập nhật nhiều bảng trong **một transaction** (xuất kho vừa phân bổ lô, vừa giảm tồn, vừa ghi sổ). Tách service sẽ phải xử lý giao dịch phân tán, dễ làm sai tồn kho.

```
backend/src/main/java/com/tung/warehouse/
├── common/        config, security, exception, response, entity (BaseEntity), util
├── identity/      người dùng, vai trò, đăng nhập, JWT, phạm vi kho
├── partner/       nhà cung cấp, khách hàng
├── catalog/       danh mục, thương hiệu, đơn vị tính, sản phẩm, biến thể
├── warehouse/     kho, khu vực, vị trí lưu trữ, phạm vi kho của người dùng
├── inventory/     lô, serial/IMEI, số dư tồn, sổ giao dịch, giữ chỗ, sự kiện serial (module lõi)
├── inbound/       nhập kho (phiếu nhập, biên bản, nhật ký)
├── outbound/      xuất kho, FIFO, phân bổ lô/serial, giá vốn, lợi nhuận gộp
├── transfer/      điều chuyển kho (xuất nguồn, vận chuyển, nhận đích)
├── stocktake/     kiểm kê (chụp số hệ thống, điều chỉnh tồn, phân công kiểm soát)
├── rma/           đổi trả (hàng tốt nhập lại, hàng lỗi vào DEFECTIVE, hết bảo hành)
├── reporting/     báo cáo nhập–xuất–tồn, giá trị tồn, lợi nhuận, tồn thấp, tồn lâu, xuất CSV
├── audit/         nhật ký thao tác (ai làm gì, lên đối tượng nào, lúc nào)
└── notification/  thông báo, Kafka consumer (kế hoạch)
```

## Công nghệ

| Lớp | Công nghệ |
|-----|-----------|
| Backend | Java 17, Spring Boot 4, Spring Data JPA (Hibernate), Spring Security (JWT, RoleHierarchy), Bean Validation |
| Cơ sở dữ liệu | MySQL 8.4 (InnoDB), Flyway migration |
| Tài liệu API | springdoc OpenAPI (Swagger UI) |
| Kiểm thử | JUnit 5, AssertJ, Spring Boot Test |
| Hạ tầng | Docker Compose, GitHub Actions |
| Kế hoạch | Redis, Kafka, Testcontainers, Angular |

## Tiến độ theo giai đoạn

| Giai đoạn | Nội dung | Trạng thái |
|-----------|----------|------------|
| 1 | Nền tảng: Spring Boot, MySQL, Flyway, xử lý lỗi thống nhất, Swagger, CI | ✅ Xong |
| 2 | JWT, phân quyền theo vai trò (RoleHierarchy), người dùng, nhà cung cấp, khách hàng, danh mục, sản phẩm, biến thể, kho, khu vực, vị trí, phạm vi kho | ✅ Xong |
| 3 | Tồn kho, lô, serial, sổ giao dịch, khóa đồng thời (pessimistic lock) | ✅ Xong |
| 4 | Nhập kho (phiếu nhập nháp, nộp, xác nhận, hủy, kiểm soát đồng thời) | ✅ Xong |
| 5 | Xuất kho FIFO, phân bổ lô/serial, giữ chỗ tồn, test đồng thời | ✅ Xong |
| 6 | Điều chuyển kho, kiểm kê, đổi trả (RMA), truy vết vòng đời serial | ✅ Xong |
| 7 | Báo cáo nhập–xuất–tồn, giá trị tồn, lợi nhuận gộp, tồn thấp, tồn lâu, xuất CSV, dashboard, xem nhật ký thao tác | ✅ Xong |
| 8 | Redis, Kafka, outbox | ⏳ Kế hoạch |
| 9 | Hoàn thiện, Docker Compose toàn hệ thống, demo, Angular | ⏳ Kế hoạch |

## API chính

| Nhóm | Endpoint | Mô tả | Quyền |
|------|----------|-------|-------|
| Xác thực | `POST /api/auth/login` | Đăng nhập, nhận accessToken + refreshToken | Public |
| Xác thực | `POST /api/auth/refresh` | Làm mới phiên (refresh token rotation) | Public |
| Người dùng | `GET /api/users` | Tìm kiếm người dùng, phân trang | Admin |
| Người dùng | `PUT /api/users/{id}/roles` | Gán vai trò | Admin |
| Người dùng | `PUT /api/users/{id}/warehouses` | Gán phạm vi kho | Admin |
| Kho | `GET /api/warehouses` | Danh sách kho được phép xem | Đã đăng nhập |
| Danh mục | `GET /api/categories` | Danh mục cây (gốc + con) | Đã đăng nhập |
| Sản phẩm | `GET /api/products` | Tìm kiếm sản phẩm kèm biến thể | Đã đăng nhập |
| Nhập kho | `POST /api/inbound-orders` | Tạo phiếu nhập, nộp, xác nhận, hủy | Staff/Manager |
| Xuất kho | `POST /api/outbound-orders` | Tạo phiếu xuất, giữ chỗ FIFO, xác nhận xuất | Staff/Manager |
| Điều chuyển | `POST /api/transfer-orders` | Tạo, duyệt, xuất nguồn, nhận đích | Staff/Manager |
| Kiểm kê | `POST /api/stocktakes` | Tạo đợt, chụp số hệ thống, nhập kết quả, duyệt | Staff/Manager |
| RMA | `POST /api/rma-orders` | Ghi nhận hàng trả, kiểm tra, xử lý (RESTOCKED/SCRAPPED) | Staff/Manager |
| Báo cáo | `GET /api/reports/stock-movement` | Nhập–xuất–tồn theo kỳ | VIEWER+ |
| Báo cáo | `GET /api/reports/stock-value` | Giá trị tồn theo giá vốn | MANAGER+ |
| Báo cáo | `GET /api/reports/gross-profit` | Lợi nhuận gộp (doanh thu − giá vốn) | MANAGER+ |
| Báo cáo | `GET /api/reports/low-stock` | Tồn thấp dưới ngưỡng | VIEWER+ |
| Báo cáo | `GET /api/reports/aging` | Tồn lâu (>90 ngày) | VIEWER+ |
| Dashboard | `GET /api/dashboard` | Tổng quan tồn, cảnh báo, phiếu chờ, biểu đồ 7 ngày | Đã đăng nhập |
| Nhật ký | `GET /api/audit-logs` | Xem nhật ký thao tác (ai làm gì, lúc nào) | Admin |

## Kỹ thuật nổi bật

### 1. JWT Stateless + Refresh Token Rotation
- Access token ngắn hạn (15 phút), refresh token dài hạn (7 ngày).
- Refresh token chỉ dùng **một lần**. Nếu phát hiện dùng lại → thu hồi toàn bộ phiên (giả định token bị đánh cắp).
- Mật khẩu băm bằng BCrypt (sự salt ngẫu nhiên, mỗi lần băm ra chuỗi khác).
- Thông báo lỗi đăng nhập chung ("Sai tên đăng nhập hoặc mật khẩu") để ngăn dò tài khoản.

```java
// JwtService.java - tạo JWT HS256
.claim("roles", roleNames)
.claim("warehouses", warehouseIds) // phạm vi kho nằm trong token
```

### 2. RoleHierarchy (Phân quyền phân cấp)
ADMIN ⊃ WAREHOUSE_MANAGER ⊃ WAREHOUSE_STAFF ⊃ VIEWER; SALES ⊃ VIEWER.  
`@PreAuthorize("hasRole('VIEWER')")` tự cho phép ADMIN, MANAGER, STAFF, SALES vào.

### 3. Pessimistic Lock + Retry Deadlock
Khi nhiều người cùng thao tác tồn kho, MySQL dùng `SELECT ... FOR UPDATE` khóa dòng. Nếu deadlock xảy ra, `Retry.onDeadlock` tự chạy lại giao dịch tối đa 3 lần.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select b from InventoryBalance b where b.id = :id")
Optional<InventoryBalance> findByIdForUpdate(@Param("id") Long id);
```

```java
// Retry.java - chạy lại nếu MySQL hủy transaction do deadlock
public static <T> T onDeadlock(int maxAttempts, Supplier<T> action) { ... }
```

### 4. Optimistic Locking (@Version)
Mọi bảng nghiệp vụ có cột `version`. Hai người cùng sửa cùng một dòng → người sau bị từ chối, không ghi đè âm thầm.

### 5. Sổ giao dịch bất biến (Append-only Ledger)
Bảng `inventory_transactions` chỉ THÊM, không sửa/xóa. Sai sót được sửa bằng giao dịch ĐẢO (dòng mới ngược dấu). Không có `updated_at`, `version`, không có setter.

### 6. FIFO & Phân bổ lô/serial
- Nhập kho tạo lô (`inventory_batches`) có giá nhập, ngày nhận, bảo hành.
- Xuất kho lấy theo FIFO (lô nhập trước lên đầu).
- Hàng serial (IMEI): mỗi thiết bị một dòng, trạng thái IN_STOCK → SOLD, mất vị trí.
- Hàng số lượng: giữ chỗ tồn, trừ tồn khi xác nhận xuất.

### 7. Kiểm kê với chụp bản sao (Snapshot)
Khi bắt đầu kiểm kê, hệ thống chụp số tồn + `version`. Khi áp dụng điều chỉnh, nếu `version` hiện tại khác (tồn đã thay đổi trong lúc đếm) → từ chối, yêu cầu đếm lại. Người duyệt không được là người đã đếm.

### 8. Báo cáo SQL thuần (JdbcTemplate)
Báo cáo tổng hợp nhiều bảng (SUM, GROUP BY) viết SQL trực tiếp qua `JdbcTemplate`, nhanh hơn JPA và không bị ràng buộc module.

```sql
SUM(CASE WHEN t.created_at >= ? AND t.quantity_delta > 0 THEN t.quantity_delta ELSE 0 END) AS qty_in
```

### 9. Xuất CSV (RFC 4180 + BOM)
File CSV có BOM đầu để Excel mở đúng tiếng Việt có dấu. Giá trị chứa dấu phẩy/nháy kép được bọc đúng chuẩn.

### 10. Audit Log kèm transaction
`AuditService.log()` chạy cùng transaction với nghiệp vụ. Nếu nghiệp vụ lỗi → rollback cả dòng log. Có `recordInNewTransaction` (`REQUIRES_NEW`) để lưu log đăng nhập thất bại dù hàm login ném 401.

## Cách chạy dự án

### Yêu cầu
Java 17, Docker Desktop, Git.

### 1. Khởi động MySQL
```powershell
cd infra/docker
Copy-Item .env.example .env
docker compose up -d
docker compose ps   # đợi trạng thái (healthy)
```

### 2. Chạy backend
```powershell
cd backend
$env:DB_PASSWORD="warehouse_local_pw"
.\mvnw.cmd spring-boot:run
```

### 3. Thử API
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Tài khoản demo (mật khẩu chung `Demo@12345`): `admin`, `manager`, `staff`, `sales`, `viewer`

## Kiểm thử

Test tích hợp chạy trên MySQL thật qua Docker:

```powershell
cd backend
$env:DB_PASSWORD="warehouse_local_pw"
.\mvnw.cmd test                      # toàn bộ test
.\mvnw.cmd test -Dtest=ReportApiTest # một lớp test
```

GitHub Actions chạy `./mvnw verify` kèm container MySQL cho mỗi push và pull request.

## Quy ước Git

- Nhánh chính: `main`. Tính năng mới làm trên nhánh `feature/<tên>`.
- Commit theo dạng: `feat:`, `fix:`, `test:`, `docs:`, `chore:`.
- Không sửa file migration đã chạy; mọi thay đổi cấu trúc bảng tạo file `V<số>__mô_tả.sql` mới.
- File `.env` chứa mật khẩu local đã nằm trong `.gitignore`, **không commit**.

## Tài liệu

Thư mục `docs/` sẽ chứa ERD, sơ đồ trạng thái phiếu và báo cáo kiểm thử.

---

*Dự án được xây dựng với mục tiêu chứng minh kỹ năng backend Java: thiết kế cơ sở dữ liệu, quản lý giao dịch, bảo mật JWT, phân quyền phức tạp, báo cáo và kiểm thử tự động trên MySQL thật.*
