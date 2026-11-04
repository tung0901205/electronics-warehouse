# Electronics Warehouse

Hệ thống quản lý kho thiết bị điện tử cho doanh nghiệp B2B: nhiều kho, theo dõi từng serial/IMEI, nhập kho, xuất kho theo FIFO, điều chuyển giữa các kho, kiểm kê, đổi trả (RMA), báo cáo và cảnh báo.

> Trạng thái: **đang phát triển** (Giai đoạn 1 xong: nền tảng, Swagger, CI. Giai đoạn 2 đang làm: đã có đăng nhập JWT và phân quyền). Xem bảng tiến độ bên dưới.

![CI](https://github.com/<tên-github-của-bạn>/electronics-warehouse/actions/workflows/ci.yml/badge.svg)

## Mục tiêu kỹ thuật

Dự án tập trung vào **tính đúng đắn của dữ liệu tồn kho**, không chỉ là CRUD:

- Mọi thay đổi tồn nằm trong một transaction và có dòng trong sổ giao dịch bất biến.
- Chống xuất vượt tồn khi nhiều người thao tác cùng lúc (pessimistic lock, optimistic lock, ràng buộc CHECK ở database).
- Phân bổ FIFO từ nhiều lô, chọn đúng serial, tính giá vốn từng lô và hạn bảo hành khi xuất.
- Có test chứng minh các quy tắc trên (test đồng thời, rollback, phân quyền).

## Công nghệ

| Lớp | Công nghệ |
|---|---|
| Backend | Java 17, Spring Boot 4, Spring Data JPA (Hibernate), Spring Security (JWT, RoleHierarchy), Bean Validation |
| Cơ sở dữ liệu | MySQL 8.4 (InnoDB), Flyway migration |
| Tài liệu API | springdoc OpenAPI (Swagger UI) |
| Kiểm thử | JUnit 5, AssertJ, Spring Boot Test |
| Hạ tầng | Docker Compose, GitHub Actions |
| Kế hoạch | Redis, Kafka, Testcontainers, Angular |

## Kiến trúc

Modular monolith: một ứng dụng Spring Boot chia thành các module theo nghiệp vụ. Mỗi module có 5 package: `controller`, `service`, `repository`, `entity`, `dto`. Phần dùng chung (cấu hình, exception, bảo mật) nằm trong `common`.

Lý do không dùng microservices: nghiệp vụ kho cần cập nhật nhiều bảng trong **một transaction** (xuất kho vừa phân bổ lô, vừa giảm tồn, vừa ghi sổ). Tách service sẽ phải xử lý giao dịch phân tán, dễ làm sai tồn kho.

```
backend/src/main/java/com/tung/warehouse/
├── common/        config, security, exception, response, entity (BaseEntity)
├── identity/      người dùng, vai trò, đăng nhập
├── partner/       nhà cung cấp, khách hàng
├── catalog/       danh mục, sản phẩm, biến thể
├── warehouse/     kho, khu vực, vị trí
├── inventory/     lô, serial, tồn, sổ giao dịch   (module lõi)
├── inbound/       nhập kho
├── outbound/      xuất kho, FIFO
├── transfer/      điều chuyển
├── stocktake/     kiểm kê
├── rma/           đổi trả
├── reporting/     báo cáo, dashboard
├── notification/  thông báo, Kafka consumer
└── audit/         nhật ký thao tác
```

## Chạy dự án

### Yêu cầu
Java 17, Docker Desktop, Git.

### 1. Khởi động MySQL
```bash
cd infra/docker
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env
docker compose up -d
docker compose ps           # đợi trạng thái (healthy)
```
Sửa mật khẩu trong `.env` nếu muốn. File `.env` không được commit.

### 2. Chạy backend
```bash
cd backend
export DB_PASSWORD=<giống DB_PASSWORD trong .env>      # PowerShell: $env:DB_PASSWORD="..."
./mvnw spring-boot:run                                  # Windows: .\mvnw.cmd spring-boot:run
```
Khi khởi động, Flyway tự tạo các bảng.

### 3. Thử API
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Đăng nhập: `POST /api/auth/login`, lấy `accessToken`, bấm **Authorize** trên Swagger rồi dán token vào.
- 5 tài khoản demo (tự tạo khi chạy lần đầu), mật khẩu chung `Demo@12345`:

| Tài khoản | Vai trò | Kế thừa |
|---|---|---|
| `admin` | ADMIN | Quản lý kho, Nhân viên kho, Người xem |
| `manager` | WAREHOUSE_MANAGER | Nhân viên kho, Người xem |
| `staff` | WAREHOUSE_STAFF | Người xem |
| `sales` | SALES | Người xem |
| `viewer` | VIEWER | không |

Đây là tài khoản chỉ dùng để học và demo. Khi triển khai thật phải đặt `app.bootstrap.enabled=false`.

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_PASSWORD` | không có (bắt buộc) | Mật khẩu của `warehouse_user` |
| `DB_URL` | `jdbc:mysql://localhost:3307/warehouse_db?...` | Địa chỉ database |
| `DB_USERNAME` | `warehouse_user` | Tên tài khoản database |
| `JWT_SECRET` | khóa mẫu dùng khi học | Khóa ký JWT, tối thiểu 32 ký tự. Triển khai thật bắt buộc tự đặt |

## Kiểm thử

Test tích hợp chạy trên MySQL thật nên cần Docker đang chạy:

```bash
cd backend
./mvnw test                                         # toàn bộ test
./mvnw test -Dtest=IdentityRepositoryTest           # một lớp test
```
GitHub Actions chạy `./mvnw verify` kèm một container MySQL cho mỗi lần push và pull request.

## Quy ước Git

- Nhánh chính: `main`. Tính năng mới làm trên nhánh `feature/<tên>`.
- Commit theo dạng: `feat:`, `fix:`, `test:`, `docs:`, `chore:`.
- Không sửa file migration đã chạy; mọi thay đổi cấu trúc bảng tạo file `V<số>__mô_tả.sql` mới.

## Tiến độ

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 1 | Nền tảng: Spring Boot, MySQL, Flyway, xử lý lỗi thống nhất, Swagger, CI | Xong |
| 2 | JWT, phân quyền theo vai trò (RoleHierarchy), CRUD dữ liệu nền, giao diện Angular | Đang làm (xong đăng nhập JWT, phân quyền, người dùng, nhà cung cấp, khách hàng, danh mục, sản phẩm và biến thể, kho, khu vực, vị trí, phạm vi kho; còn giao diện Angular) |
| 3 | Tồn kho, lô, serial, sổ giao dịch, khóa đồng thời | Code xong (InventoryService, API tra cứu tồn, test đồng thời), chờ chạy test |
| 4 | Nhập kho | Chờ test|
| 5 | Xuất kho FIFO, test đồng thời | Chưa làm |
| 6 | Điều chuyển, kiểm kê, RMA | Chưa làm |
| 7 | Báo cáo, dashboard, audit log | Chưa làm |
| 8 | Redis, Kafka, outbox | Chưa làm |
| 9 | Hoàn thiện, Docker Compose toàn hệ thống, demo | Chưa làm |

## Tài liệu

Thư mục `docs/` sẽ chứa ERD, sơ đồ trạng thái phiếu và báo cáo kiểm thử. Tài liệu đặc tả yêu cầu (use case, từ điển dữ liệu) được cập nhật song song với code.
