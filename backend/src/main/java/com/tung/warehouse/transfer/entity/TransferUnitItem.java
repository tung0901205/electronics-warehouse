package com.tung.warehouse.transfer.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Một thiết bị serial đang được chuyển trong một dòng điều chuyển (bảng trung gian dòng và thiết bị). */
@Entity
@Table(name = "transfer_unit_items")
public class TransferUnitItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_line_id", nullable = false)
    private TransferOrderLine line;

    @Column(name = "device_unit_id", nullable = false)
    private Long deviceUnitId;

    @Column(name = "serial_number", nullable = false, length = 50)
    private String serialNumber;

    @Column(nullable = false)
    private boolean received = false;

    protected TransferUnitItem() {
    }

    public TransferUnitItem(TransferOrderLine line, Long deviceUnitId, String serialNumber) {
        this.line = line;
        this.deviceUnitId = deviceUnitId;
        this.serialNumber = serialNumber;
    }

    public void markReceived() {
        this.received = true;
    }

    public Long getDeviceUnitId() { return deviceUnitId; }
    public String getSerialNumber() { return serialNumber; }
    public boolean isReceived() { return received; }
}
