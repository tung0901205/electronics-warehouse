package com.tung.warehouse.inventory.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import com.tung.warehouse.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một thiết bị cụ thể có số serial/IMEI. Chỉ có với biến thể theo dõi SERIAL. */
@Entity
@Table(name = "device_units")
public class DeviceUnit extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    // Cùng module inventory nên dùng quan hệ @ManyToOne thật sự
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Column(name = "serial_number", nullable = false, length = 50)
    private String serialNumber;

    /** Vị trí hiện tại. Rỗng (null) khi thiết bị đã rời kho (đã bán, đang vận chuyển...). */
    @Column(name = "location_id")
    private Long locationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DeviceUnitStatus status = DeviceUnitStatus.IN_STOCK;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    protected DeviceUnit() {
    }

    public DeviceUnit(Long variantId, InventoryBatch batch, String serialNumber, Long locationId) {
        this.variantId = variantId;
        this.batch = batch;
        this.serialNumber = serialNumber;
        this.locationId = locationId;
    }

    /** [MỚI Giai đoạn 5] Giữ chỗ thiết bị cho một phiếu xuất: IN_STOCK sang RESERVED. */
    public void reserve() {
        if (status != DeviceUnitStatus.IN_STOCK) {
            throw new ConflictException("Serial " + serialNumber + " đang ở trạng thái " + status + ", không thể giữ chỗ");
        }
        this.status = DeviceUnitStatus.RESERVED;
    }

    /** [MỚI Giai đoạn 5] Hủy giữ chỗ: RESERVED trở lại IN_STOCK. */
    public void unreserve() {
        this.status = DeviceUnitStatus.IN_STOCK;
    }

    /** [MỚI Giai đoạn 6] Thiết bị đến kho đích khi điều chuyển: IN_TRANSIT sang IN_STOCK tại vị trí mới. */
    public void arriveAt(Long newLocationId) {
        if (status != DeviceUnitStatus.IN_TRANSIT) {
            throw new ConflictException("Serial " + serialNumber + " đang ở trạng thái " + status + ", không phải hàng đang vận chuyển");
        }
        this.status = DeviceUnitStatus.IN_STOCK;
        this.locationId = newLocationId;
    }

    /** [MỚI Giai đoạn 6] Khách trả hàng: SOLD trở lại kho. Hàng tốt thành IN_STOCK, hàng lỗi thành DEFECTIVE. */
    public void returnToStock(Long newLocationId, boolean defective) {
        if (status != DeviceUnitStatus.SOLD) {
            throw new ConflictException("Serial " + serialNumber + " đang ở trạng thái " + status + ", không phải hàng đã bán");
        }
        this.status = defective ? DeviceUnitStatus.DEFECTIVE : DeviceUnitStatus.IN_STOCK;
        this.locationId = newLocationId;
    }

    public void setWarrantyEndDate(LocalDate warrantyEndDate) {
        this.warrantyEndDate = warrantyEndDate;
    }

    /** Thiết bị rời khỏi kho: đổi trạng thái (SOLD, IN_TRANSIT, SCRAPPED...) và bỏ vị trí. */
    public void leaveWarehouse(DeviceUnitStatus newStatus) {
        this.status = newStatus;
        this.locationId = null;
    }

    public Long getVariantId() { return variantId; }
    public InventoryBatch getBatch() { return batch; }
    public String getSerialNumber() { return serialNumber; }
    public Long getLocationId() { return locationId; }
    public DeviceUnitStatus getStatus() { return status; }
    public LocalDate getWarrantyEndDate() { return warrantyEndDate; }
}
