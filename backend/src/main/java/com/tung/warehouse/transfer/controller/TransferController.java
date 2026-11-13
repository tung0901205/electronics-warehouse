package com.tung.warehouse.transfer.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.util.Retry;
import com.tung.warehouse.transfer.dto.ReceiveTransferRequest;
import com.tung.warehouse.transfer.dto.RejectRequest;
import com.tung.warehouse.transfer.dto.TransferRequest;
import com.tung.warehouse.transfer.dto.TransferResponse;
import com.tung.warehouse.transfer.dto.TransferSummaryResponse;
import com.tung.warehouse.transfer.entity.TransferStatus;
import com.tung.warehouse.transfer.service.TransferService;
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
 * Điều chuyển kho. Quyền theo ma trận trong tài liệu đặc tả:
 * xem: Người xem trở lên (UC5.5); tạo, sửa, nộp, hủy, xuất kho nguồn, nhận hàng: Nhân viên kho trở lên (UC5.1, 5.3, 5.4);
 * duyệt, từ chối: Quản lý kho trở lên (UC5.2). Xuất kho cần vào được kho nguồn, nhận hàng cần vào được kho đích.
 */
@RestController
@RequestMapping("/api/transfer-orders")
@Tag(name = "Điều chuyển kho")
public class TransferController {

    private static final int MAX_ATTEMPTS = 3;

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Tìm kiếm phiếu điều chuyển (lọc theo mã, trạng thái, kho; có phân trang) (UC5.5)")
    public PageResponse<TransferSummaryResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) TransferStatus status,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return transferService.search(keyword, status, warehouseId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Xem chi tiết phiếu điều chuyển (UC5.5)")
    public TransferResponse getById(@PathVariable Long id) {
        return transferService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo phiếu điều chuyển nháp (UC5.1)")
    public TransferResponse create(@Valid @RequestBody TransferRequest request) {
        return transferService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Sửa phiếu nháp (UC5.1)")
    public TransferResponse update(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return transferService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Nộp phiếu chờ duyệt: DRAFT sang REQUESTED (UC5.1)")
    public TransferResponse submit(@PathVariable Long id) {
        return transferService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Duyệt phiếu: REQUESTED sang APPROVED (UC5.2)")
    public TransferResponse approve(@PathVariable Long id) {
        return transferService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Từ chối phiếu, bắt buộc có lý do (UC5.2)")
    public TransferResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return transferService.reject(id, request.reason());
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Xuất kho nguồn theo FIFO: trừ tồn, hàng chuyển sang trạng thái đang vận chuyển (UC5.3)")
    public TransferResponse ship(@PathVariable Long id) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> transferService.ship(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Nhận hàng ở kho đích: gửi kết quả nhận của từng dòng (UC5.4)")
    public TransferResponse receive(@PathVariable Long id, @Valid @RequestBody ReceiveTransferRequest request) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> transferService.receive(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Hủy phiếu chưa xuất kho nguồn")
    public TransferResponse cancel(@PathVariable Long id) {
        return transferService.cancel(id);
    }
}
