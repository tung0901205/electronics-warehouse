package com.tung.warehouse.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa khu vực. Khi sửa, mã (code) bị bỏ qua vì mã không đổi được. */
public record ZoneRequest(
        @NotBlank(message = "Mã khu vực không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{1,20}$", message = "Mã khu vực dài 1 đến 20 ký tự, chỉ gồm chữ, số, gạch dưới, gạch ngang") String code,
        @NotBlank(message = "Tên khu vực không được để trống")
        @Size(max = 100, message = "Tên khu vực tối đa 100 ký tự") String name) {
}
