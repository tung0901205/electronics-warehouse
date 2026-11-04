package com.tung.warehouse.identity.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Danh sách vai trò mới của người dùng (thay thế toàn bộ vai trò cũ). */
public record AssignRolesRequest(
        @NotEmpty(message = "Phải chọn ít nhất một vai trò") List<String> roleNames) {
}
