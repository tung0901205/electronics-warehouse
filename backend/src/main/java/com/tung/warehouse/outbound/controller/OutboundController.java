package com.tung.warehouse.outbound.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.util.Retry;
import com.tung.warehouse.outbound.dto.OutboundRequest;
import com.tung.warehouse.outbound.dto.OutboundResponse;
import com.tung.warehouse.outbound.dto.OutboundSummaryResponse;
import com.tung.warehouse.outbound.entity.OutboundStatus;
import com.tung.warehouse.outbound.service.OutboundService;
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
 * Phiếu xuất kho. Quyền theo ma trận trong tài liệu đặc tả:
 * xem: Người xem trở lên (UC4.5); tạo, sửa, nộp (giữ chỗ), hủy: Kinh doanh hoặc Nhân viên kho trở lên (UC4.1, 4.2, 4.4);
 * xác nhận xuất kho: Quản lý kho trở lên (UC4.3). Mọi thao tác chỉ trong các kho được gán.
 *
 * Các thao tác khóa nhiều dòng tồn (nộp, xác nhận, hủy) được bao bởi Retry.onDeadlock: nếu MySQL hủy transaction
 * để gỡ deadlock thì tự chạy lại, tối đa 3 lần.
 */
@RestController
@RequestMapping("/api/outbound-orders")
@Tag(name = "Xuất kho")
public class OutboundController {

    private static final int MAX_ATTEMPTS = 3;

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Tìm kiếm phiếu xuất (lọc theo mã, trạng thái, kho; có phân trang) (UC4.5)")
    public PageResponse<OutboundSummaryResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) OutboundStatus status,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return outboundService.search(keyword, status, warehouseId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Xem chi tiết phiếu xuất kèm các dòng hàng và phân bổ lô/serial (UC4.5)")
    public OutboundResponse getById(@PathVariable Long id) {
        return outboundService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tạo phiếu xuất nháp (UC4.1). Chưa giữ chỗ, chưa trừ tồn")
    public OutboundResponse create(@Valid @RequestBody OutboundRequest request) {
        return outboundService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Sửa phiếu nháp: gửi lại toàn bộ nội dung phiếu (UC4.1)")
    public OutboundResponse update(@PathVariable Long id, @Valid @RequestBody OutboundRequest request) {
        return outboundService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Nộp phiếu: giữ chỗ tồn theo FIFO, DRAFT sang RESERVED (UC4.2). Thiếu hàng thì trả 409")
    public OutboundResponse submit(@PathVariable Long id) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> outboundService.submit(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Xác nhận xuất kho: trừ tồn, ghi sổ, ghi phân bổ lô/serial và bảo hành (UC4.3)")
    public OutboundResponse confirm(@PathVariable Long id) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> outboundService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Hủy phiếu chưa xuất, trả giữ chỗ về phần khả dụng (UC4.4)")
    public OutboundResponse cancel(@PathVariable Long id) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> outboundService.cancel(id));
    }
}
