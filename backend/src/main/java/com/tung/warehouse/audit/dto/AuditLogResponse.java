package com.tung.warehouse.audit.dto;

import com.tung.warehouse.audit.entity.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponse(Long id, LocalDateTime createdAt, Long userId, String action, String entityType,
                               Long entityId, String result, String ipAddress, String detail) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getCreatedAt(), log.getUserId(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getResult(), log.getIpAddress(), log.getDetail());
    }
}
