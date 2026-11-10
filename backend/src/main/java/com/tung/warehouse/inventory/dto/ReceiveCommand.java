package com.tung.warehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lệnh nhận hàng vào kho: tạo một lô mới và cộng tồn. Module nhập kho gọi lệnh này khi xác nhận phiếu.
 * serials: danh sách số serial (bắt buộc đúng bằng quantity với biến thể SERIAL, để null với biến thể QUANTITY).
 * inboundLineId: dòng phiếu nhập sinh ra lô này (quan hệ 1-1); null nếu nhận hàng không qua phiếu nhập.
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
        String note,
        Long inboundLineId) {

    /** [MỚI Giai đoạn 4] Cách gọi ngắn khi không có dòng phiếu nhập (dùng trong test): inboundLineId = null. */
    public ReceiveCommand(Long warehouseId, Long locationId, Long variantId, String batchCode, Long supplierId,
                          LocalDateTime receivedAt, BigDecimal unitCost, int warrantyMonths, int quantity,
                          List<String> serials, String referenceType, Long referenceId, String note) {
        this(warehouseId, locationId, variantId, batchCode, supplierId, receivedAt, unitCost, warrantyMonths,
                quantity, serials, referenceType, referenceId, note, null);
    }
}
