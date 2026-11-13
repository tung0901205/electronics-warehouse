package com.tung.warehouse.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Hàng tồn lâu: lô nhận đã quá số ngày quy định mà vẫn còn hàng. */
public record AgingRow(String warehouseCode, String sku, String productName, String batchCode,
                       LocalDateTime receivedAt, long daysInStock, long onHand, BigDecimal value) {
}
