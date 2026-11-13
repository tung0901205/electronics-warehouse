package com.tung.warehouse.stocktake.controller;

import com.tung.warehouse.common.util.Retry;
import com.tung.warehouse.stocktake.dto.CountRequest;
import com.tung.warehouse.stocktake.dto.StocktakeRequest;
import com.tung.warehouse.stocktake.dto.StocktakeResponse;
import com.tung.warehouse.stocktake.service.StocktakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiểm kê. Quyền theo ma trận trong tài liệu đặc tả:
 * tạo, bắt đầu, duyệt và áp dụng: Quản lý kho trở lên (UC6.1, UC6.3);
 * nhập kết quả đếm và nộp: Nhân viên kho trở lên (UC6.2); xem: Người xem trở lên.
 */
@RestController
@RequestMapping("/api/stocktakes")
@Tag(name = "Kiểm kê")
public class StocktakeController {

    private static final int MAX_ATTEMPTS = 3;

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Danh sách các đợt kiểm kê trong các kho được phép xem")
    public List<StocktakeResponse> findVisible() {
        return stocktakeService.findVisible();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Chi tiết một đợt kiểm kê kèm các dòng")
    public StocktakeResponse getById(@PathVariable Long id) {
        return stocktakeService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Tạo đợt kiểm kê theo kho, khu vực hoặc sản phẩm (UC6.1)")
    public StocktakeResponse create(@Valid @RequestBody StocktakeRequest request) {
        return stocktakeService.create(request);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Bắt đầu đếm: chụp số hệ thống (UC6.1)")
    public StocktakeResponse start(@PathVariable Long id) {
        return stocktakeService.start(id);
    }

    @PutMapping("/{id}/counts")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Nhập số đếm thực tế cho các dòng (UC6.2)")
    public StocktakeResponse recordCounts(@PathVariable Long id, @Valid @RequestBody CountRequest request) {
        return stocktakeService.recordCounts(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Nộp kết quả đếm để chờ duyệt (UC6.2)")
    public StocktakeResponse submitCounts(@PathVariable Long id) {
        return stocktakeService.submitCounts(id);
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Duyệt và áp dụng điều chỉnh tồn. Người duyệt không được là người đã đếm (UC6.3)")
    public StocktakeResponse apply(@PathVariable Long id) {
        return Retry.onDeadlock(MAX_ATTEMPTS, () -> stocktakeService.apply(id));
    }

    @PostMapping("/{id}/restart")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Chụp lại số hệ thống để đếm lại khi tồn đã thay đổi trong lúc đếm (UC6.3)")
    public StocktakeResponse restart(@PathVariable Long id) {
        return stocktakeService.restart(id);
    }
}
