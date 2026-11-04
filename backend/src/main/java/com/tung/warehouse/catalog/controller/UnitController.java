package com.tung.warehouse.catalog.controller;

import com.tung.warehouse.catalog.dto.UnitRequest;
import com.tung.warehouse.catalog.dto.UnitResponse;
import com.tung.warehouse.catalog.service.UnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Xem (GET): mọi người đã đăng nhập. Thêm, sửa, xóa: chỉ Admin (UC2.2). */
@RestController
@RequestMapping("/api/units")
@Tag(name = "Đơn vị tính")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn vị tính")
    public List<UnitResponse> findAll() {
        return unitService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm đơn vị tính (mã tự chuyển thành chữ in hoa)")
    public UnitResponse create(@Valid @RequestBody UnitRequest request) {
        return unitService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sửa tên đơn vị tính (không đổi được mã)")
    public UnitResponse update(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return unitService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa đơn vị tính (chỉ khi chưa có biến thể dùng)")
    public void delete(@PathVariable Long id) {
        unitService.delete(id);
    }
}
