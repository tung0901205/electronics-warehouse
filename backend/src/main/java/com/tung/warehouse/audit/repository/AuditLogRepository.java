package com.tung.warehouse.audit.repository;

import com.tung.warehouse.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Nhật ký chỉ THÊM: code chỉ được dùng save(...). Màn hình xem nhật ký (UC11.2) làm ở giai đoạn 7. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
