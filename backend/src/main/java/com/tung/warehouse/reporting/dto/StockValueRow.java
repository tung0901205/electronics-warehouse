package com.tung.warehouse.reporting.dto;

import java.math.BigDecimal;

/** Giá trị tồn của một biến thể trong một kho, tính theo giá vốn của từng lô (số lượng x giá nhập). */
public record StockValueRow(Long warehouseId, String warehouseCode, Long variantId, String sku, String productName,
                            long onHand, BigDecimal value) {
}
