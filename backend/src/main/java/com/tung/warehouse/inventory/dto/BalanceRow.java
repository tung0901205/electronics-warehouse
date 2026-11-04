package com.tung.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng tồn kho kèm đủ thông tin để hiển thị (mã kho, vị trí, SKU, lô...). */
public record BalanceRow(
        Long balanceId,
        Long warehouseId,
        String warehouseCode,
        Long locationId,
        String locationCode,
        Long variantId,
        String sku,
        String productName,
        String attributes,
        String trackingType,
        Long batchId,
        String batchCode,
        LocalDateTime receivedAt,
        BigDecimal unitCost,
        int warrantyMonths,
        int onHand,
        int reserved,
        int available) {
}
