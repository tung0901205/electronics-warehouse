package com.tung.warehouse.stocktake.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import com.tung.warehouse.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

/** Một dòng kiểm kê: số hệ thống lúc chụp, số đếm thực tế, lý do chênh lệch. */
@Entity
@Table(name = "stocktake_lines")
public class StocktakeLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession session;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Column(name = "system_quantity", nullable = false)
    private int systemQuantity;

    @Column(name = "counted_quantity")
    private Integer countedQuantity;

    /** Phiên bản của dòng tồn lúc chụp: nếu sau đó tồn bị thay đổi thì phiên bản khác đi và không được ghi đè. */
    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(length = 255)
    private String reason;

    protected StocktakeLine() {
    }

    public StocktakeLine(StocktakeSession session, Long balanceId, int systemQuantity, long snapshotVersion) {
        this.session = session;
        this.balanceId = balanceId;
        this.systemQuantity = systemQuantity;
        this.snapshotVersion = snapshotVersion;
    }

    public void recordCount(int countedQuantity, String reason) {
        if (countedQuantity < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COUNT", "Số đếm không được âm");
        }
        this.countedQuantity = countedQuantity;
        this.reason = (reason == null || reason.isBlank()) ? null : reason.trim();
    }

    /** Chênh lệch = số đếm trừ số hệ thống (dương là thừa, âm là thiếu). Chưa đếm thì coi như 0. */
    public int getDifference() {
        return countedQuantity == null ? 0 : countedQuantity - systemQuantity;
    }

    public Long getBalanceId() { return balanceId; }
    public int getSystemQuantity() { return systemQuantity; }
    public Integer getCountedQuantity() { return countedQuantity; }
    public long getSnapshotVersion() { return snapshotVersion; }
    public String getReason() { return reason; }
}
