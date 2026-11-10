package com.tung.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một "mảnh" hàng đã xuất cho một dòng phiếu xuất: lấy từ lô nào, vị trí nào, bao nhiêu, giá vốn bao nhiêu.
 * Hàng theo dõi serial: mỗi thiết bị một mảnh (quantity = 1, có deviceUnitId và serialNumber).
 * Module outbound dựa vào các mảnh này để ghi outbound_allocations.
 */
public record IssuedPiece(
        Long batchId,
        String batchCode,
        Long locationId,
        Long deviceUnitId,
        String serialNumber,
        int quantity,
        BigDecimal unitCost,
        LocalDate warrantyEndDate) {
}
