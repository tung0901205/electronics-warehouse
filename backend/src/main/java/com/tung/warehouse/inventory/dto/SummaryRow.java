package com.tung.warehouse.inventory.dto;

/** Tồn của một biến thể trong một kho, cộng dồn qua mọi vị trí và lô. */
public record SummaryRow(
        Long warehouseId,
        String warehouseCode,
        Long variantId,
        String sku,
        String productName,
        String trackingType,
        long onHand,
        long reserved,
        long available) {
}
