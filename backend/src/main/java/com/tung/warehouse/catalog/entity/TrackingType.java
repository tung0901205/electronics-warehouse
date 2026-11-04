package com.tung.warehouse.catalog.entity;

/**
 * Cách theo dõi tồn kho của một biến thể.
 * SERIAL: theo dõi từng thiết bị bằng số serial/IMEI (laptop, điện thoại...).
 * QUANTITY: chỉ theo dõi số lượng theo lô (cáp sạc, chuột, ốp lưng...).
 */
public enum TrackingType {
    SERIAL,
    QUANTITY
}
