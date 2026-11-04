package com.tung.warehouse.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa kho. Khi sửa, mã (code) bị bỏ qua vì mã không đổi được. */
public record WarehouseRequest(
        @NotBlank(message = "Mã kho không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{2,20}$", message = "Mã kho dài 2 đến 20 ký tự, chỉ gồm chữ, số, gạch dưới, gạch ngang") String code,
        @NotBlank(message = "Tên kho không được để trống")
        @Size(max = 150, message = "Tên kho tối đa 150 ký tự") String name,
        @Size(max = 300, message = "Địa chỉ tối đa 300 ký tự") String address) {
}
