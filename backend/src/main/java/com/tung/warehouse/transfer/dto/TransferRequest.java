package com.tung.warehouse.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Tạo hoặc sửa phiếu điều chuyển (chỉ phiếu nháp). Lô được chọn tự động theo FIFO khi xuất kho nguồn. */
public record TransferRequest(
        @NotNull(message = "Phải chọn kho nguồn") Long sourceWarehouseId,
        @NotNull(message = "Phải chọn kho đích") Long destWarehouseId,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note,
        @NotEmpty(message = "Phiếu phải có ít nhất một dòng hàng") @Valid List<TransferLineRequest> lines) {
}
