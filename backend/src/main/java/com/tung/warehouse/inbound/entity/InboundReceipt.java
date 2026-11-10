package com.tung.warehouse.inbound.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Biên bản nhận hàng: bằng chứng ai đã xác nhận nhập kho và lúc nào. Mỗi phiếu có tối đa một biên bản. */
@Entity
@Table(name = "inbound_receipts")
public class InboundReceipt extends BaseEntity {

    @Column(name = "inbound_order_id", nullable = false, unique = true)
    private Long inboundOrderId;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(length = 500)
    private String note;

    protected InboundReceipt() {
    }

    public InboundReceipt(Long inboundOrderId, Long receivedBy, LocalDateTime receivedAt, String note) {
        this.inboundOrderId = inboundOrderId;
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
        this.note = note;
    }

    public Long getInboundOrderId() { return inboundOrderId; }
    public Long getReceivedBy() { return receivedBy; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public String getNote() { return note; }
}
