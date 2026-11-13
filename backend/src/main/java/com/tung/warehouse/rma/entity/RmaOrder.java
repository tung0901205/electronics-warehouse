package com.tung.warehouse.rma.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import com.tung.warehouse.common.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Phiếu đổi trả (RMA): khách trả lại hàng đã mua. Quy tắc chuyển trạng thái nằm ngay trong entity. */
@Entity
@Table(name = "rma_orders")
public class RmaOrder extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "outbound_order_id", nullable = false)
    private Long outboundOrderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 25)
    private RmaStatus status = RmaStatus.REQUESTED;

    @Column(nullable = false, length = 500)
    private String reason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RmaLine> lines = new ArrayList<>();

    protected RmaOrder() {
    }

    public RmaOrder(String temporaryCode, Long warehouseId, Long customerId, Long outboundOrderId, String reason) {
        this.code = temporaryCode;
        this.warehouseId = warehouseId;
        this.customerId = customerId;
        this.outboundOrderId = outboundOrderId;
        this.reason = reason;
    }

    public void assertStatus(RmaStatus... allowed) {
        for (RmaStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new ConflictException("Phiếu " + code + " đang ở trạng thái " + status + ", không thể thực hiện thao tác này");
    }

    public void addLine(RmaLine line) {
        this.lines.add(line);
    }

    /** Hàng đã về kho: REQUESTED -> RECEIVED. */
    public void markReceived() {
        assertStatus(RmaStatus.REQUESTED);
        this.status = RmaStatus.RECEIVED;
    }

    /** Đã kiểm tra và xử lý xong: RECEIVED -> INSPECTED -> CLOSED (liền nhau nên chỉ lưu trạng thái cuối). */
    public void markClosed() {
        assertStatus(RmaStatus.RECEIVED);
        this.status = RmaStatus.CLOSED;
    }

    public void assignCode(String code) {
        this.code = code;
    }

    public String getCode() { return code; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getCustomerId() { return customerId; }
    public Long getOutboundOrderId() { return outboundOrderId; }
    public RmaStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public List<RmaLine> getLines() { return lines; }
}
