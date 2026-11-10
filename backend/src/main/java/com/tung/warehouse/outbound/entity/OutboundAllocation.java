package com.tung.warehouse.outbound.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phân bổ: một dòng xuất đã lấy hàng từ lô nào, vị trí nào, serial nào, giá vốn bao nhiêu, bảo hành đến khi nào.
 * Ví dụ cần 12 sản phẩm, lô A còn 5, lô B còn 10: có hai phân bổ, 5 từ lô A và 7 từ lô B.
 */
@Entity
@Table(name = "outbound_allocations")
public class OutboundAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbound_line_id", nullable = false)
    private OutboundOrderLine line;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_code", nullable = false, length = 50)
    private String batchCode;

    @Column(name = "device_unit_id")
    private Long deviceUnitId;

    @Column(name = "serial_number", length = 50)
    private String serialNumber;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    protected OutboundAllocation() {
    }

    public OutboundAllocation(OutboundOrderLine line, Long batchId, String batchCode, Long deviceUnitId, String serialNumber,
                              Long locationId, int quantity, BigDecimal unitCost, LocalDate warrantyEndDate) {
        this.line = line;
        this.batchId = batchId;
        this.batchCode = batchCode;
        this.deviceUnitId = deviceUnitId;
        this.serialNumber = serialNumber;
        this.locationId = locationId;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.warrantyEndDate = warrantyEndDate;
    }

    /** Tổng giá vốn của phân bổ = số lượng x giá vốn của lô. */
    public BigDecimal getCostAmount() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getBatchId() { return batchId; }
    public String getBatchCode() { return batchCode; }
    public Long getDeviceUnitId() { return deviceUnitId; }
    public String getSerialNumber() { return serialNumber; }
    public Long getLocationId() { return locationId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public LocalDate getWarrantyEndDate() { return warrantyEndDate; }
}
