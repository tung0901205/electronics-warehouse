package com.tung.warehouse.identity.controller;

import com.tung.warehouse.identity.dto.RoleResponse;
import com.tung.warehouse.identity.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller chỉ nhận request và gọi service. Không chứa nghiệp vụ. */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Vai trò", description = "Tra cứu các vai trò trong hệ thống (chỉ Admin)")
@PreAuthorize("hasRole('ADMIN')") // áp dụng cho mọi hàm trong lớp: chỉ Admin được gọi
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách vai trò")
    public List<RoleResponse> findAll() {
        return roleService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy vai trò theo id")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò")
    public RoleResponse getById(@PathVariable Long id) {
        return roleService.getById(id);
    }
}
