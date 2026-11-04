package com.tung.warehouse.inventory.entity;

import com.tung.warehouse.common.entity.BaseEntity;
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
