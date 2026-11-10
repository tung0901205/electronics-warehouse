package com.tung.warehouse.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Một dòng nhật ký thao tác: ai làm gì, lên đối tượng nào, lúc nào. Chỉ thêm, không sửa, không xóa. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(nullable = false, length = 50, updatable = false)
    private String action;

    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(nullable = false, length = 20, updatable = false)
    private String result;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(length = 1000, updatable = false)
    private String detail;

    protected AuditLog() {
    }

    public AuditLog(Long userId, String action, String entityType, Long entityId, String result,
                    String ipAddress, String detail) {
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.result = result;
        this.ipAddress = ipAddress;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getResult() { return result; }
    public String getIpAddress() { return ipAddress; }
    public String getDetail() { return detail; }
}
