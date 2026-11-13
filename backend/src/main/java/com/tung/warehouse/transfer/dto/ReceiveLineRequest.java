package com.tung.warehouse.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Kết quả nhận của một dòng. receivedQuantity = 0 nghĩa là không nhận được gì (không cần vị trí).
 * Hàng theo dõi serial: serials là danh sách serial nhận được, phải đúng bằng receivedQuantity.
 */
public record ReceiveLineRequest(
        @NotNull(message = "Thiếu mã dòng") Long lineId,
        @NotNull(message = "Thiếu số lượng thực nhận") @Min(value = 0, message = "Số lượng thực nhận không được âm") Integer receivedQuantity,
        Long destLocationId,
        List<String> serials) {
}
