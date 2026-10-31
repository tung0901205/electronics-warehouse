-- V2: bổ sung các cột chung theo từ điển dữ liệu (mục 7.1 và 7.2 trong tài liệu đặc tả).
-- QUAN TRỌNG: không sửa V1 vì Flyway đã chạy và ghi checksum; mọi thay đổi mới đều tạo file V mới.

-- users: thêm số điện thoại và người tạo
ALTER TABLE users
  ADD COLUMN phone VARCHAR(20) NULL AFTER full_name,
  ADD COLUMN created_by BIGINT NULL AFTER updated_at;

-- roles: V1 chưa có các cột chung, thêm vào để mọi bảng nghiệp vụ cùng một khuôn
ALTER TABLE roles
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD COLUMN created_by BIGINT NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
