package com.tung.warehouse.stocktake.entity;

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

/** Một đợt kiểm kê. Quy tắc chuyển trạng thái nằm ngay trong entity. */
@Entity
@Table(name = "stocktake_sessions")
public class StocktakeSession extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StocktakeScope scope;

    @Column(name = "scope_zone_id")
    private Long scopeZoneId;

    @Column(name = "scope_variant_id")
    private Long scopeVariantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 25)
    private StocktakeStatus status = StocktakeStatus.DRAFT;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StocktakeLine> lines = new ArrayList<>();

    protected StocktakeSession() {
    }

    public StocktakeSession(String temporaryCode, Long warehouseId, StocktakeScope scope, Long scopeZoneId, Long scopeVariantId) {
        this.code = temporaryCode;
        this.warehouseId = warehouseId;
        this.scope = scope;
        this.scopeZoneId = scopeZoneId;
        this.scopeVariantId = scopeVariantId;
    }

    public void assertStatus(StocktakeStatus... allowed) {
        for (StocktakeStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new ConflictException("Đợt kiểm kê " + code + " đang ở trạng thái " + status + ", không thể thực hiện thao tác này");
    }

    /** Bắt đầu đếm: nhận bản chụp số hệ thống, DRAFT -> COUNTING. */
    public void start(List<StocktakeLine> snapshot) {
        assertStatus(StocktakeStatus.DRAFT);
        replaceSnapshot(snapshot);
        this.startedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Chụp lại số hệ thống để đếm lại (khi tồn đã thay đổi): PENDING_APPROVAL -> COUNTING, xóa kết quả đếm cũ. */
    public void restart(List<StocktakeLine> snapshot) {
        assertStatus(StocktakeStatus.PENDING_APPROVAL);
        replaceSnapshot(snapshot);
        this.countedBy = null;
    }

    private void replaceSnapshot(List<StocktakeLine> snapshot) {
        if (snapshot.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_STOCKTAKE", "Phạm vi kiểm kê không có dòng tồn nào");
        }
        this.lines.clear();
        this.lines.addAll(snapshot);
        this.status = StocktakeStatus.COUNTING;
    }

    /** Nộp kết quả đếm: mọi dòng phải đã đếm, dòng có chênh lệch phải có lý do. COUNTING -> PENDING_APPROVAL. */
    public void submitCounts(Long userId) {
        assertStatus(StocktakeStatus.COUNTING);
        for (StocktakeLine line : lines) {
            if (line.getCountedQuantity() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "NOT_COUNTED", "Còn dòng chưa nhập số đếm");
            }
            if (line.getDifference() != 0 && line.getReason() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Dòng có chênh lệch phải ghi lý do");
            }
        }
        this.countedBy = userId;
        this.status = StocktakeStatus.PENDING_APPROVAL;
    }

    /** Đã áp dụng điều chỉnh: PENDING_APPROVAL -> APPROVED -> APPLIED (liền nhau nên chỉ lưu trạng thái cuối). */
    public void markApplied(Long approverId) {
        assertStatus(StocktakeStatus.PENDING_APPROVAL);
        this.approvedBy = approverId;
        this.status = StocktakeStatus.APPLIED;
        this.appliedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void assignCode(String code) {
        this.code = code;
    }

    public String getCode() { return code; }
    public Long getWarehouseId() { return warehouseId; }
    public StocktakeScope getScope() { return scope; }
    public Long getScopeZoneId() { return scopeZoneId; }
    public Long getScopeVariantId() { return scopeVariantId; }
    public StocktakeStatus getStatus() { return status; }
    public Long getCountedBy() { return countedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public List<StocktakeLine> getLines() { return lines; }
}
