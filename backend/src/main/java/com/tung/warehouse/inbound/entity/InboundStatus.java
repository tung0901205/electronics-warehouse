package com.tung.warehouse.inbound.entity;

/**
 * Vòng đời phiếu nhập:
 *   DRAFT (nháp, sửa được) -> SUBMITTED (đã nộp, chờ xác nhận) -> CONFIRMED (đã nhập kho, tồn đã tăng)
 *   DRAFT hoặc SUBMITTED -> CANCELLED (đã hủy)
 * Tạo hoặc nộp phiếu KHÔNG làm tăng tồn. Chỉ xác nhận mới tăng.
 */
public enum InboundStatus {
    DRAFT,
    SUBMITTED,
    CONFIRMED,
    CANCELLED
}
