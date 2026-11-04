package com.tung.warehouse.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa đơn vị tính. Khi sửa, mã (code) bị bỏ qua vì mã không đổi được. */
public record UnitRequest(
        @NotBlank(message = "Mã đơn vị không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{1,20}$", message = "Mã đơn vị dài 1 đến 20 ký tự, chỉ gồm chữ, số, gạch dưới, gạch ngang") String code,
        @NotBlank(message = "Tên đơn vị không được để trống")
        @Size(max = 50, message = "Tên đơn vị tối đa 50 ký tự") String name) {
}
