package com.tung.warehouse.inventory.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lô hàng: một lần nhập của một biến thể, có giá nhập riêng.
 * Các khóa ngoại sang module khác (variantId, supplierId) chỉ giữ id dạng số,
 * để module inventory không phụ thuộc vào entity của catalog và partner.
 */
@Entity
@Table(name = "inventory_batches")
public class InventoryBatch extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "batch_code", nullable = false, length = 50)
    private String batchCode;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "inbound_line_id")
    private Long inboundLineId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.AVAILABLE;

    protected InventoryBatch() {
    }

    public InventoryBatch(Long variantId, String batchCode, Long supplierId, Long inboundLineId,
                          LocalDateTime receivedAt, BigDecimal unitCost, int warrantyMonths) {
        this.variantId = variantId;
        this.batchCode = batchCode;
        this.supplierId = supplierId;
        this.inboundLineId = inboundLineId;
        this.receivedAt = receivedAt;
        this.unitCost = unitCost;
        this.warrantyMonths = warrantyMonths;
    }

    public Long getVariantId() { return variantId; }
    public String getBatchCode() { return batchCode; }
    public Long getSupplierId() { return supplierId; }
    public Long getInboundLineId() { return inboundLineId; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public BigDecimal getUnitCost() { return unitCost; }
    public int getWarrantyMonths() { return warrantyMonths; }
    public BatchStatus getStatus() { return status; }
}
