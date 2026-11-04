package com.tung.warehouse.identity.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.identity.dto.AssignRolesRequest;
import com.tung.warehouse.identity.dto.AssignWarehousesRequest;
import com.tung.warehouse.identity.dto.UserCreateRequest;
import com.tung.warehouse.identity.dto.UserResponse;
import com.tung.warehouse.identity.dto.UserUpdateRequest;
import com.tung.warehouse.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý người dùng: chỉ Admin (UC1.4, UC1.5). */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Người dùng", description = "Quản lý tài khoản và phân quyền (chỉ Admin)")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm người dùng (lọc theo từ khóa, vai trò, trạng thái; có phân trang)")
    public PageResponse<UserResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return userService.search(keyword, role, enabled, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết người dùng")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản (UC1.4)")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa thông tin người dùng (UC1.4)")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Gán vai trò, thay toàn bộ vai trò cũ (UC1.5)")
    public UserResponse assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return userService.assignRoles(id, request);
    }

    @PutMapping("/{id}/warehouses")
    @Operation(summary = "Gán phạm vi kho, thay toàn bộ danh sách kho cũ (UC1.5)")
    public UserResponse assignWarehouses(@PathVariable Long id, @Valid @RequestBody AssignWarehousesRequest request) {
        return userService.assignWarehouses(id, request);
    }

    @PatchMapping("/{id}/enable")
    @Operation(summary = "Kích hoạt tài khoản")
    public UserResponse enable(@PathVariable Long id) {
        return userService.setEnabled(id, true);
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "Vô hiệu hóa tài khoản")
    public UserResponse disable(@PathVariable Long id) {
        return userService.setEnabled(id, false);
    }
}
