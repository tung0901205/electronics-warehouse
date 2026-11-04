package com.tung.warehouse.warehouse.entity;

/**
 * NORMAL: kệ/tầng thường để chứa hàng.
 * RECEIVING: khu nhận hàng (hàng vừa nhập, chưa xếp lên kệ).
 * DEFECTIVE: khu hàng lỗi (không được tính vào tồn khả dụng, không được xuất).
 * Mỗi kho tự có đúng một vị trí RECEIVING và một vị trí DEFECTIVE.
 */
public enum LocationType {
    NORMAL,
    RECEIVING,
    DEFECTIVE
}
