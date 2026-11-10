package com.tung.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Kết quả của một lần giảm tồn: thông tin lô và vị trí vừa xuất, giá vốn, hạn bảo hành (khi xuất bán). */
public record StockOutResult(Long batchId, String batchCode, Long locationId, BigDecimal unitCost, LocalDate warrantyEndDate) {
}
