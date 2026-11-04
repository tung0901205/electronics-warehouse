package com.tung.warehouse.identity.dto;

import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.entity.User;
import java.util.List;

/** Thông tin người đang đăng nhập. Không có mật khẩu. */
public record UserInfoResponse(Long id, String username, String fullName, String email,
                               List<String> roles, List<Long> warehouseIds) {

    public static UserInfoResponse from(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
        List<Long> warehouseIds = user.getWarehouseIds().stream().sorted().toList();
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                roleNames, warehouseIds);
    }
}
