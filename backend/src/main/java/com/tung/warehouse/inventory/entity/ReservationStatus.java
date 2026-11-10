package com.tung.warehouse.inventory.entity;

/** ACTIVE: đang giữ chỗ. RELEASED: đã hủy giữ chỗ (phiếu bị hủy). CONSUMED: đã dùng để xuất kho. */
public enum ReservationStatus {
    ACTIVE,
    RELEASED,
    CONSUMED
}
