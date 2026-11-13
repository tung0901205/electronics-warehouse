package com.tung.warehouse.audit.repository;

import com.tung.warehouse.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** [SỬA Giai đoạn 7] Thêm JpaSpecificationExecutor để tìm kiếm nhật ký bằng Specification (UC11.2). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
