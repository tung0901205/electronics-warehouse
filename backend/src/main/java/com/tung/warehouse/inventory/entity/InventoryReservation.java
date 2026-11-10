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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một lần giữ chỗ: dòng xuất (outboundLineId) đã "hứa" quantity sản phẩm của một dòng tồn (balance).
 * Hàng theo dõi serial có một bản ghi cho mỗi thiết bị (quantity = 1, kèm deviceUnit).
 * outboundLineId chỉ là số id (dòng phiếu xuất thuộc module outbound) để inventory không phụ thuộc outbound.
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "balance_id", nullable = false)
    private InventoryBalance balance;

    @Column(name = "outbound_line_id", nullable = false)
    private Long outboundLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_unit_id")
    private DeviceUnit deviceUnit;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    protected InventoryReservation() {
    }

    public InventoryReservation(InventoryBalance balance, Long outboundLineId, int quantity, DeviceUnit deviceUnit) {
        this.balance = balance;
        this.outboundLineId = outboundLineId;
        this.quantity = quantity;
        this.deviceUnit = deviceUnit;
    }

    public void markReleased() {
        this.status = ReservationStatus.RELEASED;
    }

    public void markConsumed() {
        this.status = ReservationStatus.CONSUMED;
    }

    public InventoryBalance getBalance() { return balance; }
    public Long getOutboundLineId() { return outboundLineId; }
    public DeviceUnit getDeviceUnit() { return deviceUnit; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
}
