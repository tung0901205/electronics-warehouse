package com.tung.warehouse.rma.entity;

/**
 * REQUESTED (đã ghi nhận yêu cầu) -> RECEIVED (hàng đã về kho) -> INSPECTED -> CLOSED (đã kiểm tra và xử lý xong).
 * INSPECTED và CLOSED xảy ra liền nhau trong cùng một transaction nên chỉ CLOSED được lưu lại.
 */
public enum RmaStatus {
    REQUESTED,
    RECEIVED,
    INSPECTED,
    CLOSED
}
