package com.tung.warehouse.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một dòng trong sổ giao dịch tồn kho. Sổ chỉ THÊM, không bao giờ sửa hay xóa:
 * entity này không có setter và cũng không kế thừa BaseEntity (không có updated_at, version).
 * Sai sót được sửa bằng một giao dịch ĐẢO (dòng mới ngược dấu), không sửa dòng cũ.
 */
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private Long locationId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    /** Có dấu: dương là tăng tồn, âm là giảm tồn. */
    @Column(name = "quantity_delta", nullable = false, updatable = false)
    private int quantityDelta;

    @Column(name = "unit_cost", precision = 18, scale = 2, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "reference_type", nullable = false, length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;

    @Column(length = 255, updatable = false)
    private String note;

    @Column(name = "performed_by", updatable = false)
    private Long performedBy;

    protected InventoryTransaction() {
    }

    public InventoryTransaction(TransactionType type, Long warehouseId, Long locationId, Long variantId, Long batchId,
                                int quantityDelta, BigDecimal unitCost, String referenceType, Long referenceId,
                                String note, Long performedBy) {
        this.type = type;
        this.warehouseId = warehouseId;
        this.locationId = locationId;
        this.variantId = variantId;
        this.batchId = batchId;
        this.quantityDelta = quantityDelta;
        this.unitCost = unitCost;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.note = note;
        this.performedBy = performedBy;
    }

    /** Tự ghi thời điểm (giờ UTC) ngay trước khi lưu lần đầu. */
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public TransactionType getType() { return type; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public Long getVariantId() { return variantId; }
    public Long getBatchId() { return batchId; }
    public int getQuantityDelta() { return quantityDelta; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public String getNote() { return note; }
    public Long getPerformedBy() { return performedBy; }
}
