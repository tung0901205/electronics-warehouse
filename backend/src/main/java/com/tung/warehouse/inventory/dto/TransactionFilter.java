package com.tung.warehouse.inventory.dto;

import com.tung.warehouse.inventory.entity.TransactionType;
import java.util.List;

/** Điều kiện tìm trong sổ giao dịch. restrictTo giống StockFilter. */
public record TransactionFilter(Long warehouseId, Long variantId, TransactionType type,
                                String referenceType, Long referenceId, List<Long> restrictTo) {
}
