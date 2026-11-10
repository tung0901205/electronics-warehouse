package com.tung.warehouse.inbound.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/**
 * Phiếu nhập kho. Quy tắc chuyển trạng thái nằm ngay trong entity (như InventoryBalance):
 * dù service nào gọi cũng không thể bỏ qua các quy tắc này.
 * Khóa ngoại sang module khác (warehouseId, supplierId) chỉ giữ id dạng số.
 */
@Entity
@Table(name = "inbound_orders")
public class InboundOrder extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private InboundStatus status = InboundStatus.DRAFT;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(length = 500)
    private String note;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InboundOrderLine> lines = new ArrayList<>();

    protected InboundOrder() {
    }

    public InboundOrder(String temporaryCode, Long warehouseId, Long supplierId, LocalDate expectedDate, String note) {
        this.code = temporaryCode;
        this.warehouseId = warehouseId;
        this.supplierId = supplierId;
        this.expectedDate = expectedDate;
        this.note = note;
    }

    // ---------- Quy tắc trạng thái ----------

    /** Phiếu phải đang ở một trong các trạng thái cho phép, nếu không báo lỗi 409. */
    public void assertStatus(InboundStatus... allowed) {
        for (InboundStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new ConflictException("Phiếu " + code + " đang ở trạng thái " + status + ", không thể thực hiện thao tác này");
    }

    /** Sửa phần đầu phiếu. Chỉ phiếu nháp mới sửa được. */
    public void updateHeader(Long warehouseId, Long supplierId, LocalDate expectedDate, String note) {
        assertStatus(InboundStatus.DRAFT);
        this.warehouseId = warehouseId;
        this.supplierId = supplierId;
        this.expectedDate = expectedDate;
        this.note = note;
    }

    /** Thay toàn bộ các dòng hàng. Chỉ phiếu nháp mới sửa được. */
    public void replaceLines(List<InboundOrderLine> newLines) {
        assertStatus(InboundStatus.DRAFT);
        this.lines.clear();
        this.lines.addAll(newLines);
    }

    /** Nộp phiếu: DRAFT -> SUBMITTED. Phiếu phải có ít nhất một dòng. */
    public void submit() {
        assertStatus(InboundStatus.DRAFT);
        if (lines.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_ORDER", "Phiếu phải có ít nhất một dòng hàng");
        }
        this.status = InboundStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Đánh dấu đã xác nhận: SUBMITTED -> CONFIRMED. Việc tăng tồn do service làm TRƯỚC khi gọi hàm này. */
    public void markConfirmed(Long userId) {
        assertStatus(InboundStatus.SUBMITTED);
        this.status = InboundStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.confirmedBy = userId;
    }

    /** Hủy phiếu chưa xác nhận. Phiếu đã xác nhận không hủy được (muốn đảo phải tạo giao dịch đảo). */
    public void cancel() {
        assertStatus(InboundStatus.DRAFT, InboundStatus.SUBMITTED);
        this.status = InboundStatus.CANCELLED;
    }

    /** Mã phiếu được gán sau khi lưu lần đầu, dựa trên id (id là duy nhất nên mã không bao giờ trùng). */
    public void assignCode(String code) {
        this.code = code;
    }

    // ---------- Tổng hợp ----------

    public int getTotalQuantity() {
        return lines.stream().mapToInt(InboundOrderLine::getQuantity).sum();
    }

    public BigDecimal getTotalAmount() {
        return lines.stream().map(InboundOrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getCode() { return code; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getSupplierId() { return supplierId; }
    public InboundStatus getStatus() { return status; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public String getNote() { return note; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public List<InboundOrderLine> getLines() { return lines; }
}
