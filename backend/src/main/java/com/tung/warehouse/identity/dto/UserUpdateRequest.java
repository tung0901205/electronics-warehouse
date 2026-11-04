package com.tung.warehouse.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sửa thông tin người dùng. Không sửa username, mật khẩu, vai trò (có chức năng riêng). */
public record UserUpdateRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự") String email,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự") String fullName,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự") String phone) {
}
