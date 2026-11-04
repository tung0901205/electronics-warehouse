package com.tung.warehouse.partner.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.partner.dto.PartnerRequest;
import com.tung.warehouse.partner.dto.PartnerResponse;
import com.tung.warehouse.partner.entity.PartnerStatus;
import com.tung.warehouse.partner.service.CustomerService;
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

/**
 * Xem (GET): mọi người đã đăng nhập. Thêm, sửa, đổi trạng thái: từ Quản lý kho trở lên (UC2.1).
 */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Khách hàng")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm khách hàng (lọc theo từ khóa, số điện thoại, trạng thái; có phân trang)")
    public PageResponse<PartnerResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "status", required = false) PartnerStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return customerService.search(keyword, phone, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết khách hàng")
    public PartnerResponse getById(@PathVariable Long id) {
        return customerService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Thêm khách hàng")
    public PartnerResponse create(@Valid @RequestBody PartnerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Sửa thông tin khách hàng (không đổi được mã)")
    public PartnerResponse update(@PathVariable Long id, @Valid @RequestBody PartnerRequest request) {
        return customerService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Kích hoạt lại khách hàng")
    public PartnerResponse activate(@PathVariable Long id) {
        return customerService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Ngừng hoạt động khách hàng")
    public PartnerResponse deactivate(@PathVariable Long id) {
        return customerService.setActive(id, false);
    }
}
