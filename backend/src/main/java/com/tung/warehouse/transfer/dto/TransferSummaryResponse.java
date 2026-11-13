package com.tung.warehouse.transfer.dto;

import java.time.LocalDateTime;

/** Một dòng trong danh sách phiếu điều chuyển. */
public record TransferSummaryResponse(
        Long id,
        String code,
        String sourceWarehouseCode,
        String destWarehouseCode,
        String status,
        int lineCount,
        int totalQuantity,
        LocalDateTime createdAt) {
}
