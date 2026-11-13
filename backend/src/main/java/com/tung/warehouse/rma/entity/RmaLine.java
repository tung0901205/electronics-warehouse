package com.tung.warehouse.rma.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một thiết bị khách trả (theo serial) và kết quả kiểm tra, xử lý. */
@Entity
@Table(name = "rma_lines")
public class RmaLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rma_order_id", nullable = false)
    private RmaOrder order;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "device_unit_id")
    private Long deviceUnitId;

    @Column(name = "serial_number", length = 50)
    private String serialNumber;

    @Column(nullable = false)
    private int quantity = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "condition_result", length = 20)
    private RmaCondition condition;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private RmaResolution resolution;

    @Column(length = 500)
    private String note;

    protected RmaLine() {
    }

    public RmaLine(RmaOrder order, Long variantId, Long deviceUnitId, String serialNumber) {
        this.order = order;
        this.variantId = variantId;
        this.deviceUnitId = deviceUnitId;
        this.serialNumber = serialNumber;
    }

    public void recordResult(RmaCondition condition, RmaResolution resolution, String note) {
        this.condition = condition;
        this.resolution = resolution;
        this.note = note;
    }

    public Long getVariantId() { return variantId; }
    public Long getDeviceUnitId() { return deviceUnitId; }
    public String getSerialNumber() { return serialNumber; }
    public RmaCondition getCondition() { return condition; }
    public RmaResolution getResolution() { return resolution; }
    public String getNote() { return note; }
}
