package com.tung.warehouse.inbound.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.inbound.dto.InboundRequest;
import com.tung.warehouse.inbound.dto.InboundResponse;
import com.tung.warehouse.inbound.dto.InboundSummaryResponse;
import com.tung.warehouse.inbound.entity.InboundStatus;
import com.tung.warehouse.inbound.service.InboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phiếu nhập kho. Quyền theo ma trận trong tài liệu đặc tả:
 * xem: Người xem trở lên (UC3.5); tạo, sửa, nộp, hủy: Nhân viên kho trở lên (UC3.1, 3.2, 3.4);
 * xác nhận nhập kho: Quản lý kho trở lên (UC3.3). Mọi thao tác chỉ trong các kho được gán.
 */
@RestController
@RequestMapping("/api/inbound-orders")
@Tag(name = "Nhập kho")
public class InboundController {

    private final InboundService inboundService;

    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Tìm kiếm phiếu nhập (lọc theo mã, trạng thái, kho; có phân trang) (UC3.5)")
    public PageResponse<InboundSummaryResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) InboundStatus status,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return inboundService.search(keyword, status, warehouseId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Xem chi tiết phiếu nhập kèm các dòng hàng (UC3.5)")
    public InboundResponse getById(@PathVariable Long id) {
        return inboundService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo phiếu nhập nháp (UC3.1). Chưa làm tăng tồn")
    public InboundResponse create(@Valid @RequestBody InboundRequest request) {
        return inboundService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Sửa phiếu nháp: gửi lại toàn bộ nội dung phiếu (UC3.1)")
    public InboundResponse update(@PathVariable Long id, @Valid @RequestBody InboundRequest request) {
        return inboundService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Nộp phiếu: DRAFT sang SUBMITTED (UC3.2). Chưa làm tăng tồn")
    public InboundResponse submit(@PathVariable Long id) {
        return inboundService.submit(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Xác nhận nhập kho: tạo lô, tạo serial, tăng tồn, ghi sổ (UC3.3)")
    public InboundResponse confirm(@PathVariable Long id) {
        return inboundService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Hủy phiếu chưa xác nhận (UC3.4)")
    public InboundResponse cancel(@PathVariable Long id) {
        return inboundService.cancel(id);
    }
}
