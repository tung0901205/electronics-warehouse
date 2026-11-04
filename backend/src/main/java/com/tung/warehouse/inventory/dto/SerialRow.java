package com.tung.warehouse.inventory.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Thông tin hiện tại của một thiết bị theo số serial. */
public record SerialRow(
        Long unitId,
        String serialNumber,
        String status,
        LocalDate warrantyEndDate,
        Long variantId,
        String sku,
        String productName,
        Long batchId,
        String batchCode,
        LocalDateTime receivedAt,
        Long locationId,
        String locationCode,
        Long warehouseId,
        String warehouseCode) {
}
