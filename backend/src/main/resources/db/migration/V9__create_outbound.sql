-- V9: phiếu xuất kho, phân bổ lô/serial và giữ chỗ tồn (mục 7.5 và 7.7 trong tài liệu đặc tả).

CREATE TABLE outbound_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,                 -- ví dụ XK-20261105-000012
  warehouse_id BIGINT NOT NULL,              -- kho xuất
  customer_id BIGINT NULL,                   -- rỗng nếu xuất nội bộ
  purpose VARCHAR(20) NOT NULL,              -- SALE hoặc INTERNAL
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT, RESERVED, CONFIRMED, COMPLETED, CANCELLED
  note VARCHAR(500) NULL,
  confirmed_at DATETIME(6) NULL,
  confirmed_by BIGINT NULL,
  CONSTRAINT uk_outbound_orders_code UNIQUE (code),
  CONSTRAINT fk_outbound_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_outbound_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB;

CREATE INDEX idx_outbound_orders_warehouse_status ON outbound_orders (warehouse_id, status);

-- Không đặt UNIQUE (phiếu, biến thể): khi sửa phiếu nháp Hibernate thêm dòng mới trước khi xóa dòng cũ nên
-- ràng buộc đó báo trùng oan. Quy tắc "mỗi biến thể một dòng" được kiểm tra trong service.
CREATE TABLE outbound_order_lines (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  outbound_order_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(18,2) NOT NULL,         -- giá bán thực tế, lưu lại như ảnh chụp tại thời điểm tạo phiếu
  CONSTRAINT ck_outbound_lines_quantity CHECK (quantity > 0),
  CONSTRAINT ck_outbound_lines_price CHECK (unit_price >= 0),
  CONSTRAINT fk_outbound_lines_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_orders (id),
  CONSTRAINT fk_outbound_lines_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB;

-- Phân bổ: một dòng xuất lấy từ những lô nào (và serial nào). Tạo khi xác nhận xuất kho.
-- batch_code và serial_number là bản chụp tại thời điểm xuất (thêm so với từ điển ban đầu) để in phiếu và tra cứu nhanh.
CREATE TABLE outbound_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  outbound_line_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  batch_code VARCHAR(50) NOT NULL,
  device_unit_id BIGINT NULL,                -- rỗng với hàng theo dõi theo số lượng
  serial_number VARCHAR(50) NULL,
  location_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_cost DECIMAL(18,2) NOT NULL,          -- giá vốn của lô
  warranty_end_date DATE NULL,               -- ngày xuất cộng số tháng bảo hành của lô
  CONSTRAINT ck_allocations_quantity CHECK (quantity > 0),
  CONSTRAINT fk_allocations_line FOREIGN KEY (outbound_line_id) REFERENCES outbound_order_lines (id),
  CONSTRAINT fk_allocations_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
  CONSTRAINT fk_allocations_unit FOREIGN KEY (device_unit_id) REFERENCES device_units (id),
  CONSTRAINT fk_allocations_location FOREIGN KEY (location_id) REFERENCES storage_locations (id)
) ENGINE=InnoDB;

CREATE INDEX idx_allocations_line ON outbound_allocations (outbound_line_id);

-- Giữ chỗ: phiếu xuất đã "hứa" một phần tồn của một dòng tồn. Hàng theo dõi serial có một dòng cho mỗi thiết bị
-- (device_unit_id, thêm so với từ điển ban đầu) để biết chính xác thiết bị nào đã được giữ.
CREATE TABLE inventory_reservations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  balance_id BIGINT NOT NULL,
  outbound_line_id BIGINT NOT NULL,
  device_unit_id BIGINT NULL,
  quantity INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, RELEASED, CONSUMED
  CONSTRAINT ck_reservations_quantity CHECK (quantity > 0),
  CONSTRAINT fk_reservations_balance FOREIGN KEY (balance_id) REFERENCES inventory_balances (id),
  CONSTRAINT fk_reservations_line FOREIGN KEY (outbound_line_id) REFERENCES outbound_order_lines (id),
  CONSTRAINT fk_reservations_unit FOREIGN KEY (device_unit_id) REFERENCES device_units (id)
) ENGINE=InnoDB;

CREATE INDEX idx_reservations_line_status ON inventory_reservations (outbound_line_id, status);
