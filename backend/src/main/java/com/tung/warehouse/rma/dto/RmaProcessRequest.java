package com.tung.warehouse.rma.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Kiểm tra và xử lý hàng trả: gửi kết quả cho TẤT CẢ các dòng của phiếu. */
public record RmaProcessRequest(@NotEmpty(message = "Phải có kết quả cho các dòng") @Valid List<RmaProcessLine> lines) {
}
