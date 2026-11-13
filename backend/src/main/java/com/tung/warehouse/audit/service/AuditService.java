package com.tung.warehouse.audit.service;

import com.tung.warehouse.audit.dto.AuditLogResponse;
import com.tung.warehouse.audit.entity.AuditLog;
import com.tung.warehouse.audit.repository.AuditLogRepository;
import com.tung.warehouse.audit.repository.AuditLogSpecs;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.PagingUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Nhật ký thao tác (UC11.1 ghi, UC11.2 xem).
 * log(...) chạy CÙNG transaction với nghiệp vụ: nghiệp vụ lỗi và bị hoàn tác thì dòng nhật ký cũng bị hoàn tác theo.
 * recordInNewTransaction(...) chạy trong transaction RIÊNG, nên vẫn được lưu dù nghiệp vụ bên ngoài sau đó ném lỗi và
 * hoàn tác. Dùng cho đăng nhập thất bại: hàm login ném lỗi 401 nhưng dòng nhật ký "đăng nhập thất bại" vẫn phải còn.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Ghi một thao tác thành công của người đang đăng nhập (cùng transaction với nghiệp vụ). */
    public void log(String action, String entityType, Long entityId, String detail) {
        record(CurrentUser.id().orElse(null), action, entityType, entityId, "SUCCESS", detail);
    }

    /** Ghi nhật ký với người thực hiện chỉ định (dùng khi chưa có đăng nhập, ví dụ lúc đăng nhập). */
    public void record(Long userId, String action, String entityType, Long entityId, String result, String detail) {
        auditLogRepository.save(new AuditLog(userId, action, entityType, entityId, result, clientIp(), detail));
    }

    /** Ghi nhật ký trong transaction riêng: được lưu ngay cả khi transaction bên ngoài bị hoàn tác. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(Long userId, String action, String entityType, Long entityId, String result, String detail) {
        record(userId, action, entityType, entityId, result, detail);
    }

    /** UC11.2: tìm nhật ký theo hành động, loại đối tượng, người thực hiện; mới nhất lên đầu, có phân trang. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String action, String entityType, Long userId, int page, int size) {
        Specification<AuditLog> spec = Specification.where(AuditLogSpecs.hasAction(action))
                .and(AuditLogSpecs.hasEntityType(entityType))
                .and(AuditLogSpecs.hasUser(userId));
        Page<AuditLog> result = auditLogRepository.findAll(spec, PagingUtil.of(page, size));
        return PageResponse.from(result.map(AuditLogResponse::from));
    }

    /** Địa chỉ IP của request đang xử lý (rỗng nếu không chạy trong một request web). */
    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
