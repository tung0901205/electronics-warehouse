package com.tung.warehouse.transfer.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Chi tiết một phiếu điều chuyển. */
public record TransferResponse(
        Long id,
        String code,
        Long sourceWarehouseId,
        String sourceWarehouseCode,
        Long destWarehouseId,
        String destWarehouseCode,
        String status,
        String note,
        String rejectReason,
        Long approvedBy,
        LocalDateTime shippedAt,
        LocalDateTime receivedAt,
        LocalDateTime createdAt,
        int totalQuantity,
        List<TransferLineResponse> lines) {
}
