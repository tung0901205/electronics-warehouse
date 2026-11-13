package com.tung.warehouse.reporting.dto;

/** Nhập–xuất–tồn của một biến thể trong một kho: tồn đầu kỳ, nhập, xuất, tồn cuối kỳ (closing = opening + qtyIn - qtyOut). */
public record StockMovementRow(Long warehouseId, String warehouseCode, Long variantId, String sku, String productName,
                               long opening, long qtyIn, long qtyOut, long closing) {
}
