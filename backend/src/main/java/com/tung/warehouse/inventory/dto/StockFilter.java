package com.tung.warehouse.inventory.dto;

import java.util.List;

/**
 * Điều kiện tìm tồn kho.
 * restrictTo: null nghĩa là không giới hạn kho (Admin); có giá trị nghĩa là chỉ được xem các kho trong danh sách.
 */
public record StockFilter(Long warehouseId, Long locationId, String keyword, boolean includeZero, List<Long> restrictTo) {
}
