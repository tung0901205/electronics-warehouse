-- V4: nhà cung cấp và khách hàng (mục 7.3 trong tài liệu đặc tả).
-- Hai bảng có cùng cấu trúc. Không xóa cứng: đối tác ngừng hợp tác chuyển status = INACTIVE.
CREATE TABLE suppliers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,
  name VARCHAR(200) NOT NULL,
  contact_name VARCHAR(150) NULL,
  phone VARCHAR(20) NULL,
  email VARCHAR(150) NULL,
  address VARCHAR(300) NULL,
  tax_code VARCHAR(20) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT uk_suppliers_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(30) NOT NULL,
  name VARCHAR(200) NOT NULL,
  contact_name VARCHAR(150) NULL,
  phone VARCHAR(20) NULL,
  email VARCHAR(150) NULL,
  address VARCHAR(300) NULL,
  tax_code VARCHAR(20) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT uk_customers_code UNIQUE (code)
) ENGINE=InnoDB;
