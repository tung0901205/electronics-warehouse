package com.tung.warehouse.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Chi tiết một phiếu xuất. grossProfit (lợi nhuận gộp = doanh thu trừ giá vốn) chỉ có khi phiếu đã xuất xong. */
public record OutboundResponse(
        Long id,
        String code,
        Long warehouseId,
        Long customerId,
        String customerName,
        String purpose,
        String status,
        String note,
        LocalDateTime confirmedAt,
        Long confirmedBy,
        LocalDateTime createdAt,
        int totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalCost,
        BigDecimal grossProfit,
        List<OutboundLineResponse> lines) {
}
