package com.tung.warehouse.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransferLineRequest(
        @NotNull(message = "Phải chọn biến thể") Long variantId,
        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải lớn hơn 0") Integer quantity) {
}
