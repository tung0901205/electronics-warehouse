package com.tung.warehouse.rma.controller;

import com.tung.warehouse.common.util.Retry;
import com.tung.warehouse.rma.dto.RmaProcessRequest;
import com.tung.warehouse.rma.dto.RmaRequest;
import com.tung.warehouse.rma.dto.RmaResponse;
import com.tung.warehouse.rma.service.RmaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đổi trả (RMA). Quyền theo ma trận trong tài liệu đặc tả:
 * tạo yêu cầu: Kinh doanh hoặc Nhân viên kho trở lên (UC7.1); nhận hàng, kiểm tra và xử lý: Nhân viên kho trở lên (UC7.2);
 * xem: Người xem trở lên. Serial hết bảo hành chỉ Quản lý kho trở lên được xử lý.
 */
@RestController
@RequestMapping("/api/rma-orders")
@Tag(name = "Đổi trả (RMA)")
public class RmaController {

    private static final int MAX_ATTEMPTS = 3;

    private final RmaService rmaService;

    public RmaController(RmaService rmaService) {
        this.rmaService = rmaService;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Danh sách phiếu đổi trả trong các kho được phép xem")
    public List<RmaResponse> findVisible() {
        return rmaService.findVisible();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Chi tiết phiếu đổi trả")
    public RmaResponse getById(@PathVariable Long id) {
        return rmaService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Ghi nhận khách trả hàng theo serial của một đơn xuất bán (UC7.1)")
    public RmaResponse create(@Valid @RequestBody RmaRequest request) {
        return rmaService.create(request);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Xác nhận hàng trả đã về kho: REQUESTED sang RECEIVED (UC7.1)")
    public RmaResponse receive(@PathVariable Long id) {
        return rmaService.receive(id);
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Kiểm tra và xử lý hàng trả: nhập lại kho (hàng tốt) hoặc đưa vào vị trí hàng lỗi (UC7.2)")
    public RmaResponse process(@PathVariable Long id, @Valid @RequestBody RmaProcessRequest request) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> rmaService.process(id, request));
    }
}
