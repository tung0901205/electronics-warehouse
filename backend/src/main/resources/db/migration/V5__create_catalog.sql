-- V5: danh mục, thương hiệu, đơn vị tính, sản phẩm, biến thể (mục 7.3 trong tài liệu đặc tả).
-- Có sẵn vài dữ liệu mẫu cho danh mục, thương hiệu, đơn vị để thử trên Swagger.

CREATE TABLE categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(100) NOT NULL,
  parent_id BIGINT NULL,                       -- NULL = danh mục gốc; có giá trị = danh mục con
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE=InnoDB;

CREATE TABLE brands (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_brands_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE units (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(50) NOT NULL,
  CONSTRAINT uk_units_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(200) NOT NULL,
  category_id BIGINT NOT NULL,
  brand_id BIGINT NULL,
  description TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
) ENGINE=InnoDB;

CREATE TABLE product_variants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  product_id BIGINT NOT NULL,
  sku VARCHAR(50) NOT NULL,
  barcode VARCHAR(50) NULL,                    -- MySQL cho phép nhiều dòng NULL trong cột UNIQUE
  attributes VARCHAR(255) NULL,
  unit_id BIGINT NOT NULL,
  tracking_type VARCHAR(20) NOT NULL,          -- SERIAL hoặc QUANTITY
  list_price DECIMAL(18,2) NOT NULL DEFAULT 0,
  warranty_months INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT uk_variants_sku UNIQUE (sku),
  CONSTRAINT uk_variants_barcode UNIQUE (barcode),
  CONSTRAINT ck_variants_price CHECK (list_price >= 0),
  CONSTRAINT ck_variants_warranty CHECK (warranty_months >= 0),
  CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT fk_variants_unit FOREIGN KEY (unit_id) REFERENCES units (id)
) ENGINE=InnoDB;

-- Dữ liệu mẫu
INSERT INTO units (code, name) VALUES ('CAI', 'Cái'), ('CHIEC', 'Chiếc'), ('BO', 'Bộ'), ('HOP', 'Hộp');
INSERT INTO brands (name) VALUES ('Apple'), ('Samsung'), ('Dell'), ('Anker');
INSERT INTO categories (name) VALUES ('Điện thoại'), ('Laptop'), ('Phụ kiện');
