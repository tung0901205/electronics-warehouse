package com.tung.warehouse.reporting.controller;

import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.reporting.dto.AgingRow;
import com.tung.warehouse.reporting.dto.LowStockRow;
import com.tung.warehouse.reporting.dto.ProfitRow;
import com.tung.warehouse.reporting.dto.StockMovementRow;
import com.tung.warehouse.reporting.dto.StockValueResponse;
import com.tung.warehouse.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Báo cáo. Quyền theo ma trận trong tài liệu đặc tả: nhập–xuất–tồn, tồn thấp, tồn lâu, xuất CSV: Người xem trở lên;
 * giá trị tồn và lợi nhuận gộp: Quản lý kho trở lên. Mỗi người chỉ thấy số liệu của các kho được gán.
 * Ngày truyền theo dạng yyyy-MM-dd; bỏ trống thì mặc định là 30 ngày gần nhất.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Báo cáo")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/stock-movement")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Báo cáo nhập – xuất – tồn theo kỳ (UC9.1)")
    public PageResponse<StockMovementRow> stockMovement(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return reportService.stockMovement(warehouseId, keyword, from, to, page, size);
    }

    @GetMapping("/stock-movement/export")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Xuất báo cáo nhập – xuất – tồn ra tệp CSV (UC9.4)")
    public ResponseEntity<byte[]> stockMovementCsv(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv("stock-movement.csv", reportService.stockMovementCsv(warehouseId, keyword, from, to));
    }

    @GetMapping("/stock-value")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Báo cáo giá trị tồn theo giá vốn của lô (UC9.2)")
    public StockValueResponse stockValue(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return reportService.stockValue(warehouseId, keyword, page, size);
    }

    @GetMapping("/stock-value/export")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Xuất báo cáo giá trị tồn ra tệp CSV (UC9.4)")
    public ResponseEntity<byte[]> stockValueCsv(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return csv("stock-value.csv", reportService.stockValueCsv(warehouseId, keyword));
    }

    @GetMapping("/gross-profit")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    @Operation(summary = "Lợi nhuận gộp theo biến thể: doanh thu trừ giá vốn của các lô đã xuất (UC9.2)")
    public List<ProfitRow> grossProfit(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.grossProfit(warehouseId, keyword, from, to);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Biến thể có tồn khả dụng không vượt ngưỡng (mặc định 5)")
    public List<LowStockRow> lowStock(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "maxQuantity", defaultValue = "5") int maxQuantity) {
        return reportService.lowStock(warehouseId, maxQuantity);
    }

    @GetMapping("/aging")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Hàng tồn lâu: lô nhận quá số ngày quy định mà vẫn còn hàng (mặc định 90 ngày)")
    public List<AgingRow> aging(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "days", defaultValue = "90") int days) {
        return reportService.aging(warehouseId, days);
    }

    /** Trả tệp CSV để trình duyệt tải về (Content-Disposition: attachment). */
    private ResponseEntity<byte[]> csv(String fileName, byte[] content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }
}
