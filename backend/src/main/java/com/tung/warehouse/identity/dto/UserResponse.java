package com.tung.warehouse.identity.dto;

import com.tung.warehouse.identity.entity.Role;
import com.tung.warehouse.identity.entity.User;
import java.time.LocalDateTime;
import java.util.List;

/** Thông tin người dùng trả cho màn hình quản trị. Không có mật khẩu. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        boolean enabled,
        List<String> roles,
        List<Long> warehouseIds,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
        List<Long> warehouseIds = user.getWarehouseIds().stream().sorted().toList();
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.isEnabled(), roleNames, warehouseIds, user.getCreatedAt());
    }
}
