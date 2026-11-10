package com.tung.warehouse.inbound.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một dòng trong danh sách phiếu nhập (không kèm các dòng hàng). */
public record InboundSummaryResponse(
        Long id,
        String code,
        Long warehouseId,
        String warehouseCode,
        Long supplierId,
        String supplierName,
        String status,
        LocalDate expectedDate,
        int lineCount,
        int totalQuantity,
        BigDecimal totalAmount,
        LocalDateTime createdAt) {
}
