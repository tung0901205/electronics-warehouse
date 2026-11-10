package com.tung.warehouse.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng trong danh sách phiếu xuất (không kèm các dòng hàng). */
public record OutboundSummaryResponse(
        Long id,
        String code,
        Long warehouseId,
        String warehouseCode,
        Long customerId,
        String customerName,
        String purpose,
        String status,
        int lineCount,
        int totalQuantity,
        BigDecimal totalAmount,
        LocalDateTime createdAt) {
}
