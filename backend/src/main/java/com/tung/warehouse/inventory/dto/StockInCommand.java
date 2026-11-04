package com.tung.warehouse.inventory.dto;

import com.tung.warehouse.inventory.entity.TransactionType;

/**
 * Lệnh tăng tồn ở một dòng tồn ĐÃ CÓ (nhận hàng điều chuyển, điều chỉnh tăng, nhận lại hàng trả).
 * Nhập hàng mới từ nhà cung cấp dùng ReceiveCommand, không dùng lệnh này.
 */
public record StockInCommand(
        Long balanceId,
        int quantity,
        TransactionType type,
        String referenceType,
        Long referenceId,
        String note) {
}
