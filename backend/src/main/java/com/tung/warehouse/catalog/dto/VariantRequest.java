package com.tung.warehouse.catalog.dto;

import com.tung.warehouse.catalog.entity.TrackingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Thêm hoặc sửa biến thể. Khi sửa, sku và trackingType phải giữ nguyên (đổi sẽ bị từ chối).
 */
public record VariantRequest(
        @NotBlank(message = "SKU không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{2,50}$", message = "SKU dài 2 đến 50 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang") String sku,

        @Size(max = 50, message = "Barcode tối đa 50 ký tự") String barcode,
        @Size(max = 255, message = "Thuộc tính tối đa 255 ký tự") String attributes,

        @NotNull(message = "Phải chọn đơn vị tính") Long unitId,
        @NotNull(message = "Phải chọn kiểu theo dõi (SERIAL hoặc QUANTITY)") TrackingType trackingType,

        @NotNull(message = "Giá niêm yết không được để trống")
        @DecimalMin(value = "0", message = "Giá niêm yết không được âm")
        @Digits(integer = 16, fraction = 2, message = "Giá niêm yết tối đa 2 chữ số thập phân") BigDecimal listPrice,

        @NotNull(message = "Số tháng bảo hành không được để trống")
        @Min(value = 0, message = "Số tháng bảo hành không được âm") Integer warrantyMonths) {
}
