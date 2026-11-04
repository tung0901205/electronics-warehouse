package com.tung.warehouse.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Admin tạo tài khoản mới (UC1.4). */
public record UserCreateRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "Tên đăng nhập dài 3 đến 50 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang") String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự") String email,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự") String fullName,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự") String phone,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu dài từ 8 đến 72 ký tự") String password,

        @NotEmpty(message = "Phải chọn ít nhất một vai trò") List<String> roleNames) {
}
