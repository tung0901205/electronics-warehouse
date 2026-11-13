package com.tung.warehouse.reporting.dto;

import java.math.BigDecimal;

/** Lợi nhuận gộp của một biến thể: doanh thu trừ giá vốn của các lô đã xuất (đơn xuất bán đã hoàn tất). */
public record ProfitRow(Long variantId, String sku, String productName, long quantity,
                        BigDecimal revenue, BigDecimal cost, BigDecimal profit) {
}
