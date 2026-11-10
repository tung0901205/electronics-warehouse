package com.tung.warehouse.outbound.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Một dòng hàng khi tạo hoặc sửa phiếu xuất. unitPrice để trống thì lấy giá niêm yết của biến thể. */
public record OutboundLineRequest(
        @NotNull(message = "Phải chọn biến thể") Long variantId,
        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải lớn hơn 0") Integer quantity,
        @DecimalMin(value = "0", message = "Giá bán không được âm")
        @Digits(integer = 16, fraction = 2, message = "Giá bán tối đa 2 chữ số thập phân") BigDecimal unitPrice) {
}
