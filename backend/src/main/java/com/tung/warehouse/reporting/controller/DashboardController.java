package com.tung.warehouse.reporting.controller;

import com.tung.warehouse.reporting.dto.DashboardResponse;
import com.tung.warehouse.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard (UC9.3): mọi người dùng đã đăng nhập đều xem được, theo các kho được gán. */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final ReportService reportService;

    public DashboardController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @Operation(summary = "Số liệu tổng quan: tồn, giá trị (Quản lý kho trở lên), cảnh báo, phiếu chờ xử lý, nhập xuất 7 ngày")
    public DashboardResponse dashboard(@RequestParam(name = "warehouseId", required = false) Long warehouseId) {
        return reportService.dashboard(warehouseId);
    }
}
