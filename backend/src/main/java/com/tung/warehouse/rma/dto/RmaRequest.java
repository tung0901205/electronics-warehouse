package com.tung.warehouse.rma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ghi nhận khách trả hàng: đơn xuất gốc, lý do và danh sách serial trả. Chỉ nhận serial thuộc đơn xuất gốc. */
public record RmaRequest(
        @NotNull(message = "Phải chọn đơn xuất gốc") Long outboundOrderId,
        @NotBlank(message = "Phải ghi lý do trả hàng")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason,
        @NotEmpty(message = "Phải có ít nhất một serial") List<String> serials) {
}
