package com.tung.warehouse.identity.dto;

import com.tung.warehouse.identity.entity.Role;

/** Dữ liệu vai trò trả ra API. Không trả thẳng entity để không lộ cấu trúc bên trong. */
public record RoleResponse(Long id, String name, String description) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription());
    }
}
