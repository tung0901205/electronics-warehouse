package com.tung.warehouse.inbound.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Chi tiết một phiếu nhập kèm các dòng hàng. */
public record InboundResponse(
        Long id,
        String code,
        Long warehouseId,
        Long supplierId,
        String supplierName,
        String status,
        LocalDate expectedDate,
        String note,
        LocalDateTime submittedAt,
        LocalDateTime confirmedAt,
        Long confirmedBy,
        LocalDateTime createdAt,
        int totalQuantity,
        BigDecimal totalAmount,
        List<InboundLineResponse> lines) {
}
