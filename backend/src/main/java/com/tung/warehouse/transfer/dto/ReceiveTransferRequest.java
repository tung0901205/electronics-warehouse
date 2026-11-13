package com.tung.warehouse.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Nhận hàng ở kho đích: gửi kết quả nhận cho TẤT CẢ các dòng của phiếu. */
public record ReceiveTransferRequest(
        @NotEmpty(message = "Phải có kết quả nhận cho các dòng") @Valid List<ReceiveLineRequest> lines) {
}
