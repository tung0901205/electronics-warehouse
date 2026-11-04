package com.tung.warehouse.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa sản phẩm cha. Biến thể (SKU, giá, bảo hành) được thêm bằng API riêng. */
public record ProductRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự") String name,
        @NotNull(message = "Phải chọn danh mục") Long categoryId,
        Long brandId,
        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") String description) {
}
