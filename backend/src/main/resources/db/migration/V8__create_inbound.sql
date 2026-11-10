-- V8: phiếu nhập kho (mục 7.6) và nhật ký thao tác (mục 7.11 trong tài liệu đặc tả).

CREATE TABLE inbound_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,                 -- ví dụ NK-20261105-000012
  warehouse_id BIGINT NOT NULL,              -- kho nhận
  supplier_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT, SUBMITTED, CONFIRMED, CANCELLED
  expected_date DATE NULL,
  note VARCHAR(500) NULL,
  submitted_at DATETIME(6) NULL,
  confirmed_at DATETIME(6) NULL,
  confirmed_by BIGINT NULL,
  CONSTRAINT uk_inbound_orders_code UNIQUE (code),
  CONSTRAINT fk_inbound_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_inbound_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
) ENGINE=InnoDB;

CREATE INDEX idx_inbound_orders_warehouse_status ON inbound_orders (warehouse_id, status);

-- Không đặt UNIQUE (phiếu, biến thể, mã lô) ở đây: khi sửa phiếu nháp, Hibernate thêm dòng mới TRƯỚC khi xóa dòng cũ
-- nên ràng buộc đó sẽ báo trùng oan. Quy tắc này được kiểm tra trong service; mã lô trùng toàn hệ thống vẫn bị
-- chặn bởi UNIQUE (variant_id, batch_code) của bảng inventory_batches.
CREATE TABLE inbound_order_lines (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  inbound_order_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL,               -- vị trí lưu của dòng
  quantity INT NOT NULL,
  unit_cost DECIMAL(18,2) NOT NULL,
  batch_code VARCHAR(50) NOT NULL,
  warranty_months INT NOT NULL DEFAULT 0,
  CONSTRAINT ck_inbound_lines_quantity CHECK (quantity > 0),
  CONSTRAINT ck_inbound_lines_cost CHECK (unit_cost >= 0),
  CONSTRAINT ck_inbound_lines_warranty CHECK (warranty_months >= 0),
  CONSTRAINT fk_inbound_lines_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_orders (id),
  CONSTRAINT fk_inbound_lines_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_inbound_lines_location FOREIGN KEY (location_id) REFERENCES storage_locations (id)
) ENGINE=InnoDB;

CREATE TABLE inbound_line_serials (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  inbound_line_id BIGINT NOT NULL,
  serial_number VARCHAR(50) NOT NULL,
  CONSTRAINT uk_inbound_serials UNIQUE (inbound_line_id, serial_number),
  CONSTRAINT fk_inbound_serials_line FOREIGN KEY (inbound_line_id) REFERENCES inbound_order_lines (id)
) ENGINE=InnoDB;

-- Biên bản nhận hàng: mỗi phiếu có tối đa một biên bản, tạo khi xác nhận nhập kho (quan hệ 1-1)
CREATE TABLE inbound_receipts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  inbound_order_id BIGINT NOT NULL,
  received_by BIGINT NOT NULL,
  received_at DATETIME(6) NOT NULL,
  note VARCHAR(500) NULL,
  CONSTRAINT uk_inbound_receipts_order UNIQUE (inbound_order_id),
  CONSTRAINT fk_inbound_receipts_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_orders (id)
) ENGINE=InnoDB;

-- Hoàn thành quan hệ 1-1 giữa dòng phiếu nhập và lô hàng (cột inbound_line_id đã có từ V7)
ALTER TABLE inventory_batches
  ADD CONSTRAINT uk_batches_inbound_line UNIQUE (inbound_line_id),
  ADD CONSTRAINT fk_batches_inbound_line FOREIGN KEY (inbound_line_id) REFERENCES inbound_order_lines (id);

-- Nhật ký thao tác: chỉ THÊM. Không có khóa ngoại tới users để nhật ký tồn tại độc lập.
-- detail dùng VARCHAR(1000) thay vì JSON để đơn giản (chỉ lưu một câu mô tả ngắn).
CREATE TABLE audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  user_id BIGINT NULL,                       -- NULL nếu chưa đăng nhập (ví dụ đăng nhập thất bại)
  action VARCHAR(50) NOT NULL,               -- ví dụ INBOUND_CONFIRM
  entity_type VARCHAR(50) NULL,
  entity_id BIGINT NULL,
  result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  ip_address VARCHAR(45) NULL,               -- đủ chỗ cho IPv6
  detail VARCHAR(1000) NULL
) ENGINE=InnoDB;

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_user_time ON audit_logs (user_id, created_at);
