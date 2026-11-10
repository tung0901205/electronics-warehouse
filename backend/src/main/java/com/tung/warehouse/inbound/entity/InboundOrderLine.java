package com.tung.warehouse.inbound.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Một dòng hàng của phiếu nhập: biến thể nào, nhập vào vị trí nào, bao nhiêu, giá nhập, mã lô, bảo hành. */
@Entity
@Table(name = "inbound_order_lines")
public class InboundOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbound_order_id", nullable = false)
    private InboundOrder order;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "batch_code", nullable = false, length = 50)
    private String batchCode;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    /** Danh sách serial. cascade = ALL: lưu hoặc xóa dòng thì serial của nó được lưu hoặc xóa theo. */
    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InboundLineSerial> serials = new ArrayList<>();

    protected InboundOrderLine() {
    }

    public InboundOrderLine(InboundOrder order, Long variantId, Long locationId, int quantity,
                            BigDecimal unitCost, String batchCode, int warrantyMonths) {
        this.order = order;
        this.variantId = variantId;
        this.locationId = locationId;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.batchCode = batchCode;
        this.warrantyMonths = warrantyMonths;
    }

    public void addSerial(String serialNumber) {
        this.serials.add(new InboundLineSerial(this, serialNumber));
    }

    public List<String> getSerialNumbers() {
        return serials.stream().map(InboundLineSerial::getSerialNumber).toList();
    }

    /** Thành tiền của dòng = số lượng x giá nhập. */
    public BigDecimal getAmount() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public InboundOrder getOrder() { return order; }
    public Long getVariantId() { return variantId; }
    public Long getLocationId() { return locationId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getBatchCode() { return batchCode; }
    public int getWarrantyMonths() { return warrantyMonths; }
}
