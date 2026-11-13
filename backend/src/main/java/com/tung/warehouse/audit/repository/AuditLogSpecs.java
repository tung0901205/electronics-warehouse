package com.tung.warehouse.audit.repository;

import com.tung.warehouse.audit.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;

/** Các điều kiện tìm nhật ký. Service ghép bằng where(...).and(...).and(...). Tham số rỗng thì return null (bỏ qua). */
public final class AuditLogSpecs {

    private AuditLogSpecs() {
    }

    // 1. Hành động, ví dụ LOGIN_FAILURE, INBOUND_CONFIRM
    public static Specification<AuditLog> hasAction(String action) {
        return (root, query, cb) -> {
            if (action == null || action.isBlank()) return null;
            return cb.equal(root.get("action"), action.trim());
        };
    }

    // 2. Loại đối tượng, ví dụ INBOUND_ORDER, USER
    public static Specification<AuditLog> hasEntityType(String entityType) {
        return (root, query, cb) -> {
            if (entityType == null || entityType.isBlank()) return null;
            return cb.equal(root.get("entityType"), entityType.trim());
        };
    }

    // 3. Người thực hiện
    public static Specification<AuditLog> hasUser(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) return null;
            return cb.equal(root.get("userId"), userId);
        };
    }
}
