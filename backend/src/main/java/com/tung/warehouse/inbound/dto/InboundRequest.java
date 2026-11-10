package com.tung.warehouse.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Tạo hoặc sửa phiếu nhập (chỉ phiếu nháp). Sửa nghĩa là gửi lại toàn bộ nội dung phiếu. */
public record InboundRequest(
        @NotNull(message = "Phải chọn kho nhận") Long warehouseId,
        @NotNull(message = "Phải chọn nhà cung cấp") Long supplierId,
        LocalDate expectedDate,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note,
        @NotEmpty(message = "Phiếu phải có ít nhất một dòng hàng") @Valid List<InboundLineRequest> lines) {
}
