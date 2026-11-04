-- V6: kho, khu vực, vị trí lưu trữ và phạm vi kho của người dùng (mục 7.4 và 7.2 trong tài liệu đặc tả).
-- Cấu trúc: kho (warehouses) 1-n khu vực (warehouse_zones) 1-n vị trí (storage_locations).

CREATE TABLE warehouses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(150) NOT NULL,
  address VARCHAR(300) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT uk_warehouses_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE warehouse_zones (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  warehouse_id BIGINT NOT NULL,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_zones_warehouse_code UNIQUE (warehouse_id, code),
  CONSTRAINT fk_zones_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB;

CREATE TABLE storage_locations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  created_by BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  zone_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  location_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',   -- NORMAL, RECEIVING hoặc DEFECTIVE
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT uk_locations_zone_code UNIQUE (zone_id, code),
  CONSTRAINT fk_locations_zone FOREIGN KEY (zone_id) REFERENCES warehouse_zones (id)
) ENGINE=InnoDB;

-- Bảng trung gian n-n giữa users và warehouses: người dùng nào được làm việc ở kho nào
CREATE TABLE user_warehouse_access (
  user_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, warehouse_id),
  CONSTRAINT fk_uwa_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_uwa_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB;

-- Dữ liệu mẫu: một kho, ba khu vực (SYS là khu hệ thống chứa 2 vị trí đặc biệt), vài kệ/tầng
INSERT INTO warehouses (code, name, address) VALUES ('KHO-HN', 'Kho Hà Nội', 'Hà Nội');

INSERT INTO warehouse_zones (warehouse_id, code, name)
  SELECT id, 'SYS', 'Khu hệ thống' FROM warehouses WHERE code = 'KHO-HN';
INSERT INTO warehouse_zones (warehouse_id, code, name)
  SELECT id, 'A', 'Khu A (điện thoại, laptop)' FROM warehouses WHERE code = 'KHO-HN';
INSERT INTO warehouse_zones (warehouse_id, code, name)
  SELECT id, 'B', 'Khu B (phụ kiện)' FROM warehouses WHERE code = 'KHO-HN';

INSERT INTO storage_locations (zone_id, code, location_type)
  SELECT z.id, 'RECEIVING', 'RECEIVING' FROM warehouse_zones z JOIN warehouses w ON w.id = z.warehouse_id
  WHERE w.code = 'KHO-HN' AND z.code = 'SYS';
INSERT INTO storage_locations (zone_id, code, location_type)
  SELECT z.id, 'DEFECTIVE', 'DEFECTIVE' FROM warehouse_zones z JOIN warehouses w ON w.id = z.warehouse_id
  WHERE w.code = 'KHO-HN' AND z.code = 'SYS';
INSERT INTO storage_locations (zone_id, code)
  SELECT z.id, c.code FROM warehouse_zones z JOIN warehouses w ON w.id = z.warehouse_id
  JOIN (SELECT 'A1-T1' AS code UNION ALL SELECT 'A1-T2' UNION ALL SELECT 'A1-T3') c
  WHERE w.code = 'KHO-HN' AND z.code = 'A';
INSERT INTO storage_locations (zone_id, code)
  SELECT z.id, c.code FROM warehouse_zones z JOIN warehouses w ON w.id = z.warehouse_id
  JOIN (SELECT 'B2-T1' AS code UNION ALL SELECT 'B2-T2') c
  WHERE w.code = 'KHO-HN' AND z.code = 'B';
