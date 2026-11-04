package com.tung.warehouse.partner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu tạo hoặc sửa nhà cung cấp, khách hàng (dùng chung vì hai bên giống nhau).
 * Khi sửa, trường code bị bỏ qua vì mã không được đổi.
 */
public record PartnerRequest(
        @NotBlank(message = "Mã không được để trống")
        @Size(max = 30, message = "Mã tối đa 30 ký tự") String code,

        @NotBlank(message = "Tên không được để trống")
        @Size(max = 200, message = "Tên tối đa 200 ký tự") String name,

        @Size(max = 150, message = "Tên người liên hệ tối đa 150 ký tự") String contactName,
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự") String phone,
        @Email(message = "Email không đúng định dạng") @Size(max = 150, message = "Email tối đa 150 ký tự") String email,
        @Size(max = 300, message = "Địa chỉ tối đa 300 ký tự") String address,
        @Size(max = 20, message = "Mã số thuế tối đa 20 ký tự") String taxCode) {
}
