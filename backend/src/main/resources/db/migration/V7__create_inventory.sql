-- V7: tồn kho (mục 7.5 trong tài liệu đặc tả): lô, serial, số dư tồn, sổ giao dịch.
-- Chưa có inventory_reservations: bảng đó gắn với dòng phiếu xuất nên sẽ tạo ở giai đoạn 5.

-- Lô hàng: mỗi lần nhập một biến thể tạo một lô (có giá nhập, ngày nhận, bảo hành)
CREATE TABLE inventory_batches (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  variant_id BIGINT NOT NULL,
  batch_code VARCHAR(50) NOT NULL,
  supplier_id BIGINT NULL,
  inbound_line_id BIGINT NULL,               -- khóa ngoại tới dòng phiếu nhập sẽ thêm ở giai đoạn 4
  received_at DATETIME(6) NOT NULL,          -- ngày nhận hàng, dùng để xếp thứ tự FIFO
  unit_cost DECIMAL(18,2) NOT NULL,
  warranty_months INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  CONSTRAINT uk_batches_variant_code UNIQUE (variant_id, batch_code),
  CONSTRAINT ck_batches_cost CHECK (unit_cost >= 0),
  CONSTRAINT ck_batches_warranty CHECK (warranty_months >= 0),
  CONSTRAINT fk_batches_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_batches_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
) ENGINE=InnoDB;

CREATE INDEX idx_batches_fifo ON inventory_batches (variant_id, received_at);

-- Từng thiết bị có serial/IMEI (chỉ dùng cho biến thể theo dõi SERIAL)
CREATE TABLE device_units (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  variant_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  serial_number VARCHAR(50) NOT NULL,
  location_id BIGINT NULL,                   -- NULL khi đã bán hoặc đang vận chuyển
  status VARCHAR(20) NOT NULL DEFAULT 'IN_STOCK',
  warranty_end_date DATE NULL,
  CONSTRAINT uk_units_variant_serial UNIQUE (variant_id, serial_number),
  CONSTRAINT fk_units_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_units_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
  CONSTRAINT fk_units_location FOREIGN KEY (location_id) REFERENCES storage_locations (id)
) ENGINE=InnoDB;

CREATE INDEX idx_units_batch ON device_units (batch_id);
CREATE INDEX idx_units_location_status ON device_units (location_id, status);

-- Số dư tồn: mỗi dòng là một tổ hợp (kho, vị trí, biến thể, lô)
CREATE TABLE inventory_balances (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  warehouse_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  on_hand INT NOT NULL DEFAULT 0,            -- tồn thực tế
  reserved INT NOT NULL DEFAULT 0,           -- đã giữ chỗ cho phiếu xuất
  CONSTRAINT uk_balances_key UNIQUE (warehouse_id, location_id, variant_id, batch_id),
  -- Lớp bảo vệ cuối cùng ở database: dù code sai cũng không thể làm tồn âm
  CONSTRAINT ck_balances_on_hand CHECK (on_hand >= 0),
  CONSTRAINT ck_balances_reserved CHECK (reserved >= 0),
  CONSTRAINT ck_balances_reserved_le_on_hand CHECK (reserved <= on_hand),
  CONSTRAINT fk_balances_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_balances_location FOREIGN KEY (location_id) REFERENCES storage_locations (id),
  CONSTRAINT fk_balances_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_balances_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id)
) ENGINE=InnoDB;

CREATE INDEX idx_balances_variant_warehouse ON inventory_balances (variant_id, warehouse_id);

-- Sổ giao dịch: CHỈ THÊM, không sửa, không xóa (vì vậy không có updated_at, version).
-- Không có khóa ngoại tới users: sổ cái phải tồn tại độc lập, kể cả khi tài khoản bị dọn.
CREATE TABLE inventory_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  type VARCHAR(30) NOT NULL,                 -- INBOUND, OUTBOUND, TRANSFER_OUT, TRANSFER_IN, ADJUST, RMA_IN
  warehouse_id BIGINT NOT NULL,              -- thêm so với từ điển ban đầu, để báo cáo theo kho đơn giản và nhanh
  location_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  quantity_delta INT NOT NULL,               -- có dấu: dương là tăng, âm là giảm
  unit_cost DECIMAL(18,2) NULL,
  reference_type VARCHAR(30) NOT NULL,       -- loại phiếu gốc, ví dụ INBOUND_ORDER
  reference_id BIGINT NOT NULL,              -- mã phiếu gốc (tham chiếu mềm, không có khóa ngoại)
  note VARCHAR(255) NULL,
  performed_by BIGINT NULL,                  -- NULL nếu do hệ thống thực hiện
  CONSTRAINT ck_transactions_delta CHECK (quantity_delta <> 0),
  CONSTRAINT fk_transactions_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_transactions_location FOREIGN KEY (location_id) REFERENCES storage_locations (id),
  CONSTRAINT fk_transactions_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_transactions_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id)
) ENGINE=InnoDB;

CREATE INDEX idx_transactions_warehouse_time ON inventory_transactions (warehouse_id, created_at);
CREATE INDEX idx_transactions_variant_time ON inventory_transactions (variant_id, created_at);
CREATE INDEX idx_transactions_reference ON inventory_transactions (reference_type, reference_id);
