package com.tung.warehouse.inventory.dto;

import com.tung.warehouse.inventory.entity.TransactionType;
import java.util.List;

/**
 * Lệnh giảm tồn ở một dòng tồn (xuất kho, xuất điều chuyển, điều chỉnh giảm).
 * fromReserved: true nếu lấy từ phần đã giữ chỗ (xác nhận phiếu xuất đã giữ chỗ).
 * serials: bắt buộc đúng bằng quantity với biến thể SERIAL, để null với biến thể QUANTITY.
 */
public record StockOutCommand(
        Long balanceId,
        int quantity,
        TransactionType type,
        boolean fromReserved,
        List<String> serials,
        String referenceType,
        Long referenceId,
        String note) {
}
