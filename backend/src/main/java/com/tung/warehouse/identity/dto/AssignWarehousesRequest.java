package com.tung.warehouse.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Danh sách kho mới của người dùng (thay thế toàn bộ). Danh sách rỗng nghĩa là gỡ hết kho. */
public record AssignWarehousesRequest(
        @NotNull(message = "Danh sách kho không được để trống (có thể là danh sách rỗng)") List<Long> warehouseIds) {
}
