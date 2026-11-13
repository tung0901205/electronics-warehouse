package com.tung.warehouse.reporting.dto;

public record LowStockRow(Long warehouseId, String warehouseCode, Long variantId, String sku, String productName, long available) {
}
