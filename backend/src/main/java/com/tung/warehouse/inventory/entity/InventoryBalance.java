package com.tung.warehouse.inventory.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

/**
 * Số dư tồn của một tổ hợp (kho, vị trí, biến thể, lô).
 *
 * Quy tắc nghiệp vụ nằm NGAY TRONG entity (không rải rác ở service):
 *   - tồn khả dụng = tồn thực tế - đã giữ chỗ
 *   - không bao giờ cho tồn thực tế hoặc số giữ chỗ bị âm
 *   - số giữ chỗ không được vượt tồn thực tế
 * Nhờ vậy dù ai gọi, quy tắc cũng luôn được áp dụng, và test được mà không cần database.
 */
@Entity
@Table(name = "inventory_balances")
public class InventoryBalance extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    protected InventoryBalance() {
    }

    public InventoryBalance(Long warehouseId, Long locationId, Long variantId, InventoryBatch batch, int onHand) {
        this.warehouseId = warehouseId;
        this.locationId = locationId;
        this.variantId = variantId;
        this.batch = batch;
        this.onHand = onHand;
        this.reserved = 0;
    }

    /** Tồn khả dụng: phần có thể giữ chỗ hoặc xuất. */
    public int getAvailable() {
        return onHand - reserved;
    }

    public void increase(int quantity) {
        this.onHand += quantity;
    }

    /**
     * Giảm tồn thực tế.
     * fromReserved = false: lấy từ phần khả dụng (xuất thẳng, điều chỉnh giảm...).
     * fromReserved = true: lấy từ phần đã giữ chỗ (xác nhận xuất một phiếu đã giữ chỗ trước đó).
     */
    public void decrease(int quantity, boolean fromReserved) {
        if (fromReserved) {
            if (quantity > reserved) {
                throw new InsufficientStockException(quantity, reserved);
            }
            this.reserved -= quantity;
            this.onHand -= quantity;
        } else {
            if (quantity > getAvailable()) {
                throw new InsufficientStockException(quantity, getAvailable());
            }
            this.onHand -= quantity;
        }
    }

    /** Giữ chỗ: chỉ giữ được trong phần khả dụng. Tồn thực tế không đổi. */
    public void reserve(int quantity) {
        if (quantity > getAvailable()) {
            throw new InsufficientStockException(quantity, getAvailable());
        }
        this.reserved += quantity;
    }

    /** Hủy giữ chỗ: không thể trả lại nhiều hơn số đang giữ. */
    public void release(int quantity) {
        if (quantity > reserved) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_RELEASE",
                    "Không thể hủy giữ chỗ " + quantity + ": chỉ đang giữ " + reserved);
        }
        this.reserved -= quantity;
    }

    public Long getWarehouseId() { return warehouseId; }
    public Long getLocationId() { return locationId; }
    public Long getVariantId() { return variantId; }
    public InventoryBatch getBatch() { return batch; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
}
