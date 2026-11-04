package com.tung.warehouse.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa danh mục. parentId = null nghĩa là danh mục gốc. */
public record CategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự") String name,
        Long parentId) {
}
