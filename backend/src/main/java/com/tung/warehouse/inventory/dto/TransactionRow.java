package com.tung.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng trong sổ giao dịch kèm thông tin hiển thị. */
public record TransactionRow(
        Long id,
        LocalDateTime createdAt,
        String type,
        Long warehouseId,
        String warehouseCode,
        Long locationId,
        String locationCode,
        Long variantId,
        String sku,
        String productName,
        Long batchId,
        String batchCode,
        int quantityDelta,
        BigDecimal unitCost,
        String referenceType,
        Long referenceId,
        String note,
        Long performedBy,
        String performedByUsername) {
}
