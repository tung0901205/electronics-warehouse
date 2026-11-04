package com.tung.warehouse.inventory.entity;

/** Loại giao dịch trong sổ tồn kho. */
public enum TransactionType {
    INBOUND,       // nhập kho
    OUTBOUND,      // xuất kho
    TRANSFER_OUT,  // xuất khỏi kho nguồn khi điều chuyển
    TRANSFER_IN,   // nhận vào kho đích khi điều chuyển
    ADJUST,        // điều chuyển số liệu sau kiểm kê
    RMA_IN         // nhận lại hàng khách trả
}
