-- V10: điều chuyển kho (mục 7.8), kiểm kê (7.9), đổi trả RMA (7.10) và nhật ký sự kiện của từng serial.

-- Nhật ký sự kiện của từng thiết bị có serial: nhập, chuyển, bán, nhận hàng trả... (chỉ THÊM, không sửa/xóa).
-- Dùng cho UC8.3 "truy vết vòng đời một serial". Bảng này thêm so với từ điển ban đầu.
CREATE TABLE device_unit_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  device_unit_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,                 -- cùng bộ giá trị với inventory_transactions.type
  from_location_id BIGINT NULL,
  to_location_id BIGINT NULL,
  reference_type VARCHAR(30) NOT NULL,
  reference_id BIGINT NOT NULL,
  performed_by BIGINT NULL,
  CONSTRAINT fk_unit_events_unit FOREIGN KEY (device_unit_id) REFERENCES device_units (id),
  CONSTRAINT fk_unit_events_from FOREIGN KEY (from_location_id) REFERENCES storage_locations (id),
  CONSTRAINT fk_unit_events_to FOREIGN KEY (to_location_id) REFERENCES storage_locations (id)
) ENGINE=InnoDB;

CREATE INDEX idx_unit_events_unit ON device_unit_events (device_unit_id, id);

-- ===================== ĐIỀU CHUYỂN KHO =====================
CREATE TABLE transfer_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,                 -- ví dụ DC-20261105-000012
  source_warehouse_id BIGINT NOT NULL,
  dest_warehouse_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  note VARCHAR(500) NULL,
  reject_reason VARCHAR(255) NULL,           -- thêm so với từ điển: lý do từ chối (bắt buộc khi REJECTED)
  approved_by BIGINT NULL,
  shipped_at DATETIME(6) NULL,
  received_at DATETIME(6) NULL,
  CONSTRAINT uk_transfer_orders_code UNIQUE (code),
  CONSTRAINT ck_transfer_orders_warehouses CHECK (source_warehouse_id <> dest_warehouse_id),
  CONSTRAINT fk_transfer_orders_source FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_transfer_orders_dest FOREIGN KEY (dest_warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB;

CREATE TABLE transfer_order_lines (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  transfer_order_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  batch_id BIGINT NULL,                      -- xác định khi xuất kho nguồn; một dòng có thể được tách thành nhiều dòng theo lô
  quantity INT NOT NULL,
  received_quantity INT NULL,                -- số thực nhận ở kho đích
  dest_location_id BIGINT NULL,
  CONSTRAINT ck_transfer_lines_quantity CHECK (quantity > 0),
  CONSTRAINT ck_transfer_lines_received CHECK (received_quantity IS NULL OR received_quantity >= 0),
  CONSTRAINT fk_transfer_lines_order FOREIGN KEY (transfer_order_id) REFERENCES transfer_orders (id),
  CONSTRAINT fk_transfer_lines_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_transfer_lines_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches (id),
  CONSTRAINT fk_transfer_lines_location FOREIGN KEY (dest_location_id) REFERENCES storage_locations (id)
) ENGINE=InnoDB;

-- Bảng trung gian n-n giữa dòng điều chuyển và thiết bị serial. Dùng khóa chính riêng (id) kèm UNIQUE cặp
-- thay cho khóa chính kép để code JPA đơn giản; serial_number là bản chụp để hiển thị.
CREATE TABLE transfer_unit_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  transfer_line_id BIGINT NOT NULL,
  device_unit_id BIGINT NOT NULL,
  serial_number VARCHAR(50) NOT NULL,
  received BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT uk_transfer_unit_items UNIQUE (transfer_line_id, device_unit_id),
  CONSTRAINT fk_transfer_items_line FOREIGN KEY (transfer_line_id) REFERENCES transfer_order_lines (id),
  CONSTRAINT fk_transfer_items_unit FOREIGN KEY (device_unit_id) REFERENCES device_units (id)
) ENGINE=InnoDB;

-- ===================== KIỂM KÊ =====================
CREATE TABLE stocktake_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,                 -- ví dụ KK-20261105-000012
  warehouse_id BIGINT NOT NULL,
  scope VARCHAR(20) NOT NULL,                -- WAREHOUSE, ZONE hoặc PRODUCT
  scope_zone_id BIGINT NULL,                 -- thêm so với từ điển: khu vực cần kiểm (khi scope = ZONE)
  scope_variant_id BIGINT NULL,              -- thêm so với từ điển: biến thể cần kiểm (khi scope = PRODUCT)
  status VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
  counted_by BIGINT NULL,
  approved_by BIGINT NULL,
  started_at DATETIME(6) NULL,
  applied_at DATETIME(6) NULL,
  CONSTRAINT uk_stocktake_sessions_code UNIQUE (code),
  CONSTRAINT fk_stocktake_sessions_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB;

CREATE TABLE stocktake_lines (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  session_id BIGINT NOT NULL,
  balance_id BIGINT NOT NULL,
  system_quantity INT NOT NULL,              -- số hệ thống lúc bắt đầu
  counted_quantity INT NULL,
  snapshot_version BIGINT NOT NULL,          -- phiên bản dòng tồn lúc chụp, để phát hiện xung đột
  reason VARCHAR(255) NULL,
  CONSTRAINT ck_stocktake_lines_counted CHECK (counted_quantity IS NULL OR counted_quantity >= 0),
  CONSTRAINT fk_stocktake_lines_session FOREIGN KEY (session_id) REFERENCES stocktake_sessions (id),
  CONSTRAINT fk_stocktake_lines_balance FOREIGN KEY (balance_id) REFERENCES inventory_balances (id)
) ENGINE=InnoDB;

CREATE TABLE stock_adjustments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  stocktake_line_id BIGINT NOT NULL,
  transaction_id BIGINT NOT NULL,
  quantity_delta INT NOT NULL,
  reason VARCHAR(255) NOT NULL,
  approved_by BIGINT NOT NULL,
  CONSTRAINT uk_stock_adjustments_line UNIQUE (stocktake_line_id),
  CONSTRAINT fk_stock_adjustments_line FOREIGN KEY (stocktake_line_id) REFERENCES stocktake_lines (id),
  CONSTRAINT fk_stock_adjustments_tx FOREIGN KEY (transaction_id) REFERENCES inventory_transactions (id)
) ENGINE=InnoDB;

-- ===================== ĐỔI TRẢ (RMA) =====================
CREATE TABLE rma_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,                 -- ví dụ RMA-20261105-000012
  warehouse_id BIGINT NOT NULL,              -- thêm so với từ điển: kho của đơn xuất gốc, nơi nhận hàng trả
  customer_id BIGINT NOT NULL,
  outbound_order_id BIGINT NOT NULL,
  status VARCHAR(25) NOT NULL DEFAULT 'REQUESTED',   -- REQUESTED, RECEIVED, INSPECTED, CLOSED
  reason VARCHAR(500) NOT NULL,
  CONSTRAINT uk_rma_orders_code UNIQUE (code),
  CONSTRAINT fk_rma_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
  CONSTRAINT fk_rma_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
  CONSTRAINT fk_rma_orders_outbound FOREIGN KEY (outbound_order_id) REFERENCES outbound_orders (id)
) ENGINE=InnoDB;

CREATE TABLE rma_lines (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  rma_order_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  device_unit_id BIGINT NULL,
  serial_number VARCHAR(50) NULL,            -- thêm so với từ điển: bản chụp serial để hiển thị
  quantity INT NOT NULL DEFAULT 1,
  condition_result VARCHAR(20) NULL,         -- GOOD hoặc DEFECTIVE
  resolution VARCHAR(30) NULL,               -- RESTOCKED, RETURNED_TO_SUPPLIER, SCRAPPED
  note VARCHAR(500) NULL,
  CONSTRAINT ck_rma_lines_quantity CHECK (quantity > 0),
  CONSTRAINT fk_rma_lines_order FOREIGN KEY (rma_order_id) REFERENCES rma_orders (id),
  CONSTRAINT fk_rma_lines_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
  CONSTRAINT fk_rma_lines_unit FOREIGN KEY (device_unit_id) REFERENCES device_units (id)
) ENGINE=InnoDB;
