package com.tung.warehouse.outbound.dto;

import com.tung.warehouse.outbound.entity.OutboundPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Tạo hoặc sửa phiếu xuất (chỉ phiếu nháp). customerId bắt buộc khi purpose là SALE, bị bỏ qua khi INTERNAL. */
public record OutboundRequest(
        @NotNull(message = "Phải chọn kho xuất") Long warehouseId,
        @NotNull(message = "Phải chọn mục đích xuất (SALE hoặc INTERNAL)") OutboundPurpose purpose,
        Long customerId,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note,
        @NotEmpty(message = "Phiếu phải có ít nhất một dòng hàng") @Valid List<OutboundLineRequest> lines) {
}
