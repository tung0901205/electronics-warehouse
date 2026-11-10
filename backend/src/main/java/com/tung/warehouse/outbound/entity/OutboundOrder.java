package com.tung.warehouse.outbound.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/**
 * Phiếu xuất kho. Quy tắc chuyển trạng thái nằm ngay trong entity, giống InboundOrder.
 * Khóa ngoại sang module khác (warehouseId, customerId) chỉ giữ id dạng số.
 */
@Entity
@Table(name = "outbound_orders")
public class OutboundOrder extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OutboundPurpose purpose;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OutboundStatus status = OutboundStatus.DRAFT;

    @Column(length = 500)
    private String note;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OutboundOrderLine> lines = new ArrayList<>();

    protected OutboundOrder() {
    }

    public OutboundOrder(String temporaryCode, Long warehouseId, Long customerId, OutboundPurpose purpose, String note) {
        this.code = temporaryCode;
        this.warehouseId = warehouseId;
        this.customerId = customerId;
        this.purpose = purpose;
        this.note = note;
    }

    // ---------- Quy tắc trạng thái ----------

    public void assertStatus(OutboundStatus... allowed) {
        for (OutboundStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new ConflictException("Phiếu " + code + " đang ở trạng thái " + status + ", không thể thực hiện thao tác này");
    }

    /** Sửa phần đầu phiếu. Chỉ phiếu nháp mới sửa được. */
    public void updateHeader(Long warehouseId, Long customerId, OutboundPurpose purpose, String note) {
        assertStatus(OutboundStatus.DRAFT);
        this.warehouseId = warehouseId;
        this.customerId = customerId;
        this.purpose = purpose;
        this.note = note;
    }

    /** Thay toàn bộ các dòng hàng. Chỉ phiếu nháp mới sửa được. */
    public void replaceLines(List<OutboundOrderLine> newLines) {
        assertStatus(OutboundStatus.DRAFT);
        this.lines.clear();
        this.lines.addAll(newLines);
    }

    /** Đã giữ chỗ xong: DRAFT -> RESERVED. Việc giữ chỗ do service làm TRƯỚC khi gọi hàm này. */
    public void markReserved() {
        assertStatus(OutboundStatus.DRAFT);
        if (lines.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_ORDER", "Phiếu phải có ít nhất một dòng hàng");
        }
        this.status = OutboundStatus.RESERVED;
    }

    /** Đã xuất kho xong: RESERVED -> CONFIRMED -> COMPLETED (liền nhau nên chỉ lưu trạng thái cuối). */
    public void markCompleted(Long userId) {
        assertStatus(OutboundStatus.RESERVED);
        this.status = OutboundStatus.COMPLETED;
        this.confirmedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.confirmedBy = userId;
    }

    /** Hủy phiếu chưa xuất. Phiếu đã xuất xong không hủy được. */
    public void cancel() {
        assertStatus(OutboundStatus.DRAFT, OutboundStatus.RESERVED);
        this.status = OutboundStatus.CANCELLED;
    }

    public void assignCode(String code) {
        this.code = code;
    }

    // ---------- Tổng hợp ----------

    public int getTotalQuantity() {
        return lines.stream().mapToInt(OutboundOrderLine::getQuantity).sum();
    }

    public BigDecimal getTotalAmount() {
        return lines.stream().map(OutboundOrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCost() {
        return lines.stream().map(OutboundOrderLine::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getCode() { return code; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getCustomerId() { return customerId; }
    public OutboundPurpose getPurpose() { return purpose; }
    public OutboundStatus getStatus() { return status; }
    public String getNote() { return note; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public List<OutboundOrderLine> getLines() { return lines; }
}
