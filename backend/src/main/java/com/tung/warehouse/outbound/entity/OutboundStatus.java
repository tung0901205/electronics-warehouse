package com.tung.warehouse.outbound.entity;

/**
 * Vòng đời phiếu xuất:
 *   DRAFT (nháp, sửa được) -> RESERVED (đã giữ chỗ tồn) -> CONFIRMED -> COMPLETED (đã xuất, tồn đã giảm)
 *   DRAFT hoặc RESERVED -> CANCELLED (đã hủy, giữ chỗ được trả lại)
 * Hiện nay CONFIRMED và COMPLETED xảy ra liền nhau trong cùng một transaction nên chỉ COMPLETED được lưu lại;
 * CONFIRMED giữ lại cho đúng đặc tả, dùng khi sau này tách bước giao hàng.
 */
public enum OutboundStatus {
    DRAFT,
    RESERVED,
    CONFIRMED,
    COMPLETED,
    CANCELLED
}
