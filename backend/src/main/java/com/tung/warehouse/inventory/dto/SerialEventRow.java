package com.tung.warehouse.inventory.dto;

import java.time.LocalDateTime;

/** Một sự kiện trong vòng đời của một serial (nhập, chuyển đi, chuyển đến, bán, nhận hàng trả...). */
public record SerialEventRow(
        Long id,
        LocalDateTime createdAt,
        String type,
        String serialNumber,
        String sku,
        String fromWarehouse,
        String fromLocation,
        String toWarehouse,
        String toLocation,
        String referenceType,
        Long referenceId,
        String performedByUsername) {
}
