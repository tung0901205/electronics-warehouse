package com.tung.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lệnh nhận hàng vào kho: tạo một lô mới và cộng tồn. Module nhập kho (giai đoạn 4) sẽ gọi lệnh này.
 * serials: danh sách số serial (bắt buộc đúng bằng quantity với biến thể SERIAL, để null với biến thể QUANTITY).
 */
public record ReceiveCommand(
        Long warehouseId,
        Long locationId,
        Long variantId,
        String batchCode,
        Long supplierId,
        LocalDateTime receivedAt,
        BigDecimal unitCost,
        int warrantyMonths,
        int quantity,
        List<String> serials,
        String referenceType,
        Long referenceId,
        String note) {
}
