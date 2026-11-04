package com.tung.warehouse.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LocationRequest(
        @NotBlank(message = "Mã vị trí không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{1,30}$", message = "Mã vị trí dài 1 đến 30 ký tự, chỉ gồm chữ, số, gạch dưới, gạch ngang") String code) {
}
