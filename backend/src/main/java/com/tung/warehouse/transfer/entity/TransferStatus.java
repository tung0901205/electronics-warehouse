package com.tung.warehouse.transfer.entity;

/**
 * Vòng đời phiếu điều chuyển:
 *   DRAFT -> REQUESTED (đã nộp, chờ duyệt) -> APPROVED (đã duyệt) -> IN_TRANSIT (đã xuất khỏi kho nguồn, đang vận chuyển)
 *   -> RECEIVED (đã nhận đủ) hoặc PARTIALLY_RECEIVED (nhận thiếu)
 *   REQUESTED -> REJECTED (bị từ chối); DRAFT, REQUESTED hoặc APPROVED -> CANCELLED (đã hủy)
 * Tạo hoặc duyệt phiếu KHÔNG làm đổi tồn. Tồn kho nguồn giảm khi xuất; tồn kho đích chỉ tăng khi nhận.
 */
public enum TransferStatus {
    DRAFT,
    REQUESTED,
    APPROVED,
    IN_TRANSIT,
    PARTIALLY_RECEIVED,
    RECEIVED,
    REJECTED,
    CANCELLED
}
