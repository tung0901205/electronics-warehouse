package com.tung.warehouse.transfer.entity;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Một dòng điều chuyển. Lúc tạo phiếu chỉ biết biến thể và số lượng; lô (batchId) được xác định khi xuất kho nguồn
 * theo FIFO. Nếu một dòng phải lấy từ nhiều lô thì dòng được tách thành nhiều dòng, mỗi dòng đúng một lô.
 */
@Entity
@Table(name = "transfer_order_lines")
public class TransferOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false)
    private TransferOrder order;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Column(name = "dest_location_id")
    private Long destLocationId;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<TransferUnitItem> unitItems = new ArrayList<>();

    protected TransferOrderLine() {
    }

    public TransferOrderLine(TransferOrder order, Long variantId, Long batchId, int quantity) {
        this.order = order;
        this.variantId = variantId;
        this.batchId = batchId;
        this.quantity = quantity;
    }

    /** Khi xuất kho nguồn: gán lô thật sự được lấy và số lượng lấy từ lô đó. */
    public void assignBatch(Long batchId, int quantity) {
        this.batchId = batchId;
        this.quantity = quantity;
    }

    public void addUnitItem(TransferUnitItem item) {
        this.unitItems.add(item);
    }

    /** Ghi kết quả nhận hàng ở kho đích. */
    public void markReceived(int receivedQuantity, Long destLocationId) {
        this.receivedQuantity = receivedQuantity;
        this.destLocationId = destLocationId;
    }

    public List<String> getSerialNumbers() {
        return unitItems.stream().map(TransferUnitItem::getSerialNumber).toList();
    }

    public List<String> getReceivedSerialNumbers() {
        return unitItems.stream().filter(TransferUnitItem::isReceived).map(TransferUnitItem::getSerialNumber).toList();
    }

    public TransferOrder getOrder() { return order; }
    public Long getVariantId() { return variantId; }
    public Long getBatchId() { return batchId; }
    public int getQuantity() { return quantity; }
    public Integer getReceivedQuantity() { return receivedQuantity; }
    public Long getDestLocationId() { return destLocationId; }
    public List<TransferUnitItem> getUnitItems() { return unitItems; }
}
