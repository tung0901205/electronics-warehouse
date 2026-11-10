package com.tung.warehouse.outbound.entity;

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

/** Một dòng hàng của phiếu xuất: biến thể nào, bao nhiêu, bán giá bao nhiêu. */
@Entity
@Table(name = "outbound_order_lines")
public class OutboundOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbound_order_id", nullable = false)
    private OutboundOrder order;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /** Các phân bổ lô/serial, chỉ có sau khi phiếu đã xuất kho. */
    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<OutboundAllocation> allocations = new ArrayList<>();

    protected OutboundOrderLine() {
    }

    public OutboundOrderLine(OutboundOrder order, Long variantId, int quantity, BigDecimal unitPrice) {
        this.order = order;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public void addAllocation(OutboundAllocation allocation) {
        this.allocations.add(allocation);
    }

    /** Doanh thu của dòng = số lượng x giá bán. */
    public BigDecimal getAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Tổng giá vốn của dòng = tổng giá vốn các phân bổ (bằng 0 nếu chưa xuất kho). */
    public BigDecimal getCostAmount() {
        return allocations.stream().map(OutboundAllocation::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public OutboundOrder getOrder() { return order; }
    public Long getVariantId() { return variantId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public List<OutboundAllocation> getAllocations() { return allocations; }
}
