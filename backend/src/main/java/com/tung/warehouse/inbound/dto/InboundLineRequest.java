package com.tung.warehouse.inbound.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Một dòng hàng khi tạo hoặc sửa phiếu nhập.
 * warrantyMonths để trống thì dùng số tháng bảo hành mặc định của biến thể.
 * serials: danh sách serial (chỉ cho biến thể SERIAL). Phiếu nháp được phép thiếu serial, nhưng nộp phiếu thì phải đủ.
 */
public record InboundLineRequest(
        @NotNull(message = "Phải chọn biến thể") Long variantId,
        @NotNull(message = "Phải chọn vị trí lưu") Long locationId,
        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải lớn hơn 0") Integer quantity,
        @NotNull(message = "Giá nhập không được để trống")
        @DecimalMin(value = "0", message = "Giá nhập không được âm")
        @Digits(integer = 16, fraction = 2, message = "Giá nhập tối đa 2 chữ số thập phân") BigDecimal unitCost,
        @NotBlank(message = "Mã lô không được để trống")
        @Size(max = 50, message = "Mã lô tối đa 50 ký tự") String batchCode,
        @Min(value = 0, message = "Số tháng bảo hành không được âm") Integer warrantyMonths,
        List<String> serials) {
}
