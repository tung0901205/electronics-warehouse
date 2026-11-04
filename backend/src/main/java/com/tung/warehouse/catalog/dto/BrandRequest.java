package com.tung.warehouse.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank(message = "Tên thương hiệu không được để trống")
        @Size(max = 100, message = "Tên thương hiệu tối đa 100 ký tự") String name) {
}
