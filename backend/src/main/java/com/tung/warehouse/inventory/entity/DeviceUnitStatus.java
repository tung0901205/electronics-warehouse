package com.tung.warehouse.inventory.entity;

/** Trạng thái của từng thiết bị có serial. */
public enum DeviceUnitStatus {
    IN_STOCK,    // đang nằm trong kho, sẵn sàng xuất
    RESERVED,    // đã giữ chỗ cho một phiếu xuất
    SOLD,        // đã xuất bán
    IN_TRANSIT,  // đang vận chuyển giữa hai kho
    DEFECTIVE,   // hàng lỗi
    SCRAPPED     // đã hủy hoặc mất
}
