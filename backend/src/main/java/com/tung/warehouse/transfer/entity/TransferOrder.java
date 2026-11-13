package com.tung.warehouse.transfer.entity;

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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** Phiếu điều chuyển hàng từ kho nguồn sang kho đích. Quy tắc chuyển trạng thái nằm ngay trong entity. */
@Entity
@Table(name = "transfer_orders")
public class TransferOrder extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "source_warehouse_id", nullable = false)
    private Long sourceWarehouseId;

    @Column(name = "dest_warehouse_id", nullable = false)
    private Long destWarehouseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.DRAFT;

    @Column(length = 500)
    private String note;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TransferOrderLine> lines = new ArrayList<>();

    protected TransferOrder() {
    }

    public TransferOrder(String temporaryCode, Long sourceWarehouseId, Long destWarehouseId, String note) {
        this.code = temporaryCode;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destWarehouseId = destWarehouseId;
        this.note = note;
    }

    // ---------- Quy tắc trạng thái ----------

    public void assertStatus(TransferStatus... allowed) {
        for (TransferStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new ConflictException("Phiếu " + code + " đang ở trạng thái " + status + ", không thể thực hiện thao tác này");
    }

    /** Sửa nội dung phiếu nháp. */
    public void updateContent(Long sourceWarehouseId, Long destWarehouseId, String note, List<TransferOrderLine> newLines) {
        assertStatus(TransferStatus.DRAFT);
        this.sourceWarehouseId = sourceWarehouseId;
        this.destWarehouseId = destWarehouseId;
        this.note = note;
        this.lines.clear();
        this.lines.addAll(newLines);
    }

    /** Nộp phiếu: DRAFT -> REQUESTED. */
    public void submit() {
        assertStatus(TransferStatus.DRAFT);
        if (lines.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_ORDER", "Phiếu phải có ít nhất một dòng hàng");
        }
        this.status = TransferStatus.REQUESTED;
    }

    public void approve(Long userId) {
        assertStatus(TransferStatus.REQUESTED);
        this.status = TransferStatus.APPROVED;
        this.approvedBy = userId;
    }

    public void reject(Long userId, String reason) {
        assertStatus(TransferStatus.REQUESTED);
        this.status = TransferStatus.REJECTED;
        this.approvedBy = userId;
        this.rejectReason = reason;
    }

    /** Đã xuất khỏi kho nguồn: APPROVED -> IN_TRANSIT. Việc trừ tồn do service làm TRƯỚC khi gọi hàm này. */
    public void markShipped() {
        assertStatus(TransferStatus.APPROVED);
        this.status = TransferStatus.IN_TRANSIT;
        this.shippedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Đã nhận ở kho đích: IN_TRANSIT -> RECEIVED nếu nhận đủ, PARTIALLY_RECEIVED nếu nhận thiếu. Chỉ nhận được một lần. */
    public void markReceived(boolean complete) {
        assertStatus(TransferStatus.IN_TRANSIT);
        this.status = complete ? TransferStatus.RECEIVED : TransferStatus.PARTIALLY_RECEIVED;
        this.receivedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Hủy phiếu chưa xuất kho nguồn. */
    public void cancel() {
        assertStatus(TransferStatus.DRAFT, TransferStatus.REQUESTED, TransferStatus.APPROVED);
        this.status = TransferStatus.CANCELLED;
    }

    public void assignCode(String code) {
        this.code = code;
    }

    /** Thêm một dòng (dùng khi tách dòng theo lô lúc xuất kho nguồn). */
    public void addLine(TransferOrderLine line) {
        this.lines.add(line);
    }

    public int getTotalQuantity() {
        return lines.stream().mapToInt(TransferOrderLine::getQuantity).sum();
    }

    public String getCode() { return code; }
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public Long getDestWarehouseId() { return destWarehouseId; }
    public TransferStatus getStatus() { return status; }
    public String getNote() { return note; }
    public String getRejectReason() { return rejectReason; }
    public Long getApprovedBy() { return approvedBy; }
    public LocalDateTime getShippedAt() { return shippedAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public List<TransferOrderLine> getLines() { return lines; }
}
