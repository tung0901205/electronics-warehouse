package com.tung.warehouse.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một sự kiện trong "cuộc đời" của một thiết bị serial: nhập kho, chuyển đi, chuyển đến, bán, nhận hàng trả...
 * Chỉ THÊM, không sửa, không xóa (giống sổ giao dịch). Dùng để truy vết một serial từ lúc nhập đến hiện tại.
 */
@Entity
@Table(name = "device_unit_events")
public class DeviceUnitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_unit_id", nullable = false, updatable = false)
    private DeviceUnit deviceUnit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private TransactionType type;

    @Column(name = "from_location_id", updatable = false)
    private Long fromLocationId;

    @Column(name = "to_location_id", updatable = false)
    private Long toLocationId;

    @Column(name = "reference_type", nullable = false, length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;

    @Column(name = "performed_by", updatable = false)
    private Long performedBy;

    protected DeviceUnitEvent() {
    }

    public DeviceUnitEvent(DeviceUnit deviceUnit, TransactionType type, Long fromLocationId, Long toLocationId,
                           String referenceType, Long referenceId, Long performedBy) {
        this.deviceUnit = deviceUnit;
        this.type = type;
        this.fromLocationId = fromLocationId;
        this.toLocationId = toLocationId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.performedBy = performedBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
}
