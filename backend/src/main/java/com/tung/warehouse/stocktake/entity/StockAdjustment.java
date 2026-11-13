package com.tung.warehouse.stocktake.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Bản ghi điều chỉnh tồn sinh ra từ một dòng kiểm kê có chênh lệch (quan hệ 1-1 với dòng kiểm kê). */
@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment extends BaseEntity {

    @Column(name = "stocktake_line_id", nullable = false, unique = true)
    private Long stocktakeLineId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "approved_by", nullable = false)
    private Long approvedBy;

    protected StockAdjustment() {
    }

    public StockAdjustment(Long stocktakeLineId, Long transactionId, int quantityDelta, String reason, Long approvedBy) {
        this.stocktakeLineId = stocktakeLineId;
        this.transactionId = transactionId;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
        this.approvedBy = approvedBy;
    }

    public Long getStocktakeLineId() { return stocktakeLineId; }
    public Long getTransactionId() { return transactionId; }
    public int getQuantityDelta() { return quantityDelta; }
}
