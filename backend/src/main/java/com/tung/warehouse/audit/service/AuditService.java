package com.tung.warehouse.audit.service;

import com.tung.warehouse.audit.entity.AuditLog;
import com.tung.warehouse.audit.repository.AuditLogRepository;
import com.tung.warehouse.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi nhật ký thao tác (UC11.1). Module nào cần ghi thì gọi log(...) một cách tường minh,
 * ví dụ InboundService gọi sau mỗi lần tạo, nộp, xác nhận, hủy phiếu.
 * Hàm log chạy CÙNG transaction với nghiệp vụ: nghiệp vụ lỗi và bị hoàn tác thì dòng nhật ký cũng bị hoàn tác theo.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, String entityType, Long entityId, String detail) {
        auditLogRepository.save(new AuditLog(CurrentUser.id().orElse(null), action, entityType, entityId,
                "SUCCESS", clientIp(), detail));
    }

    /** Địa chỉ IP của request đang xử lý (rỗng nếu không chạy trong một request web). */
    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
