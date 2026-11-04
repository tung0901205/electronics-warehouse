package com.tung.warehouse.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu cũ không được để trống") String oldPassword,
        // BCrypt chỉ dùng tối đa 72 byte đầu của mật khẩu nên giới hạn 72
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu mới dài từ 8 đến 72 ký tự") String newPassword) {
}
