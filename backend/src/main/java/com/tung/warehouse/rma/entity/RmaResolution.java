package com.tung.warehouse.rma.entity;

/** Cách xử lý: RESTOCKED (nhập lại kho, chỉ cho hàng tốt); RETURNED_TO_SUPPLIER (trả nhà cung cấp) và SCRAPPED (hủy) chỉ cho hàng lỗi. */
public enum RmaResolution {
    RESTOCKED,
    RETURNED_TO_SUPPLIER,
    SCRAPPED
}
