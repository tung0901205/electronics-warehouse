package com.tung.warehouse.stocktake.entity;

/**
 * DRAFT (vừa tạo) -> COUNTING (đã chụp số hệ thống, đang đếm) -> PENDING_APPROVAL (đã nộp kết quả đếm, chờ duyệt)
 * -> APPROVED -> APPLIED (đã áp dụng điều chỉnh vào tồn kho).
 * APPROVED và APPLIED xảy ra liền nhau trong cùng một transaction nên chỉ APPLIED được lưu lại.
 */
public enum StocktakeStatus {
    DRAFT,
    COUNTING,
    PENDING_APPROVAL,
    APPROVED,
    APPLIED
}
