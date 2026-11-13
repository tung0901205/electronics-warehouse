package com.tung.warehouse.stocktake.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Số đếm của một dòng. Dòng có chênh lệch với số hệ thống phải ghi lý do (kiểm tra khi nộp kết quả). */
public record CountEntry(
        @NotNull(message = "Thiếu mã dòng") Long lineId,
        @NotNull(message = "Thiếu số đếm") @Min(value = 0, message = "Số đếm không được âm") Integer countedQuantity,
        @Size(max = 255, message = "Lý do tối đa 255 ký tự") String reason) {
}
