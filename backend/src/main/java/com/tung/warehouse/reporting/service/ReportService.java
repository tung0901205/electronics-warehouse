package com.tung.warehouse.reporting.service;

import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.response.PageResponse;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.common.util.CsvWriter;
import com.tung.warehouse.common.util.PagingUtil;
import com.tung.warehouse.reporting.dto.AgingRow;
import com.tung.warehouse.reporting.dto.DailyPoint;
import com.tung.warehouse.reporting.dto.DashboardResponse;
import com.tung.warehouse.reporting.dto.LowStockRow;
import com.tung.warehouse.reporting.dto.ProfitRow;
import com.tung.warehouse.reporting.dto.ReportFilter;
import com.tung.warehouse.reporting.dto.StockMovementRow;
import com.tung.warehouse.reporting.dto.StockValueResponse;
import com.tung.warehouse.reporting.dto.StockValueRow;
import com.tung.warehouse.reporting.repository.ReportQueryRepository;
import com.tung.warehouse.warehouse.service.WarehouseAccessService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo (UC9.1, UC9.2, UC9.4) và dashboard (UC9.3). Chỉ đọc dữ liệu, không sửa gì.
 * Mọi báo cáo chỉ gồm các kho người dùng được phép xem. Ngày trong báo cáo tính theo giờ UTC (giống cách lưu dữ liệu).
 */
@Service
public class ReportService {

    private static final int LOW_STOCK_THRESHOLD = 5;     // ngưỡng "tồn thấp" trên dashboard
    private static final int AGING_DAYS = 90;             // số ngày để coi là "tồn lâu" trên dashboard
    private static final int EXPORT_LIMIT = 100_000;      // số dòng tối đa khi xuất CSV

    private final ReportQueryRepository repository;
    private final WarehouseAccessService accessService;
    private final WarehouseService warehouseService;

    public ReportService(ReportQueryRepository repository, WarehouseAccessService accessService, WarehouseService warehouseService) {
        this.repository = repository;
        this.accessService = accessService;
        this.warehouseService = warehouseService;
    }

    // ==================== NHẬP – XUẤT – TỒN (UC9.1) ====================

    @Transactional(readOnly = true)
    public PageResponse<StockMovementRow> stockMovement(Long warehouseId, String keyword, LocalDate from, LocalDate to, int page, int size) {
        ReportFilter filter = filter(warehouseId, keyword);
        LocalDate end = to == null ? today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        checkRange(start, end);
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toExclusive = end.plusDays(1).atStartOfDay();   // "đến hết ngày end"

        Pageable pageable = PagingUtil.of(page, size);
        long total = repository.countStockMovement(filter, fromTime, toExclusive);
        List<StockMovementRow> rows = total == 0 ? List.of()
                : repository.findStockMovement(filter, fromTime, toExclusive, pageable.getOffset(), pageable.getPageSize());
        return toPage(rows, total, pageable);
    }

    /** Xuất CSV (UC9.4): cùng số liệu với báo cáo nhưng lấy toàn bộ, không phân trang. */
    @Transactional(readOnly = true)
    public byte[] stockMovementCsv(Long warehouseId, String keyword, LocalDate from, LocalDate to) {
        ReportFilter filter = filter(warehouseId, keyword);
        LocalDate end = to == null ? today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        checkRange(start, end);
        List<StockMovementRow> rows = repository.findStockMovement(filter, start.atStartOfDay(), end.plusDays(1).atStartOfDay(), 0, EXPORT_LIMIT);

        List<List<Object>> lines = new ArrayList<>();
        for (StockMovementRow r : rows) {
            lines.add(List.of(r.warehouseCode(), r.sku(), r.productName(), r.opening(), r.qtyIn(), r.qtyOut(), r.closing()));
        }
        return CsvWriter.toBytes(List.of("warehouse", "sku", "product", "opening", "in", "out", "closing"), lines);
    }

    // ==================== GIÁ TRỊ TỒN VÀ LỢI NHUẬN (UC9.2) ====================

    @Transactional(readOnly = true)
    public StockValueResponse stockValue(Long warehouseId, String keyword, int page, int size) {
        ReportFilter filter = filter(warehouseId, keyword);
        Pageable pageable = PagingUtil.of(page, size);
        long total = repository.countStockValue(filter);
        List<StockValueRow> rows = total == 0 ? List.of() : repository.findStockValue(filter, pageable.getOffset(), pageable.getPageSize());
        return new StockValueResponse(repository.totalStockValue(filter), toPage(rows, total, pageable));
    }

    @Transactional(readOnly = true)
    public byte[] stockValueCsv(Long warehouseId, String keyword) {
        List<StockValueRow> rows = repository.findStockValue(filter(warehouseId, keyword), 0, EXPORT_LIMIT);
        List<List<Object>> lines = new ArrayList<>();
        for (StockValueRow r : rows) {
            lines.add(List.of(r.warehouseCode(), r.sku(), r.productName(), r.onHand(), r.value()));
        }
        return CsvWriter.toBytes(List.of("warehouse", "sku", "product", "on_hand", "value"), lines);
    }

    @Transactional(readOnly = true)
    public List<ProfitRow> grossProfit(Long warehouseId, String keyword, LocalDate from, LocalDate to) {
        ReportFilter filter = filter(warehouseId, keyword);
        LocalDate end = to == null ? today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        checkRange(start, end);
        return repository.findGrossProfit(filter, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    // ==================== TỒN THẤP, TỒN LÂU ====================

    @Transactional(readOnly = true)
    public List<LowStockRow> lowStock(Long warehouseId, int maxQuantity) {
        return repository.findLowStock(filter(warehouseId, null), maxQuantity);
    }

    @Transactional(readOnly = true)
    public List<AgingRow> aging(Long warehouseId, int days) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return repository.findAging(filter(warehouseId, null), now.minusDays(days), now);
    }

    // ==================== DASHBOARD (UC9.3) ====================

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long warehouseId) {
        ReportFilter filter = filter(warehouseId, null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> totals = repository.stockTotals(filter);

        // Giá trị tồn (giá vốn) chỉ cho Quản lý kho trở lên
        BigDecimal totalValue = CurrentUser.isManagerOrAbove() ? (BigDecimal) totals.get("value") : null;
        int warehouses = warehouseId != null ? 1 : warehouseService.findVisible().size();

        return new DashboardResponse(
                warehouses,
                ((Number) totals.get("variants")).longValue(),
                ((Number) totals.get("on_hand")).longValue(),
                totalValue,
                repository.countLowStock(filter, LOW_STOCK_THRESHOLD),
                repository.countAging(filter, now.minusDays(AGING_DAYS)),
                repository.countPending("inbound_orders", "t.status = 'SUBMITTED'", "t.warehouse_id", filter),
                repository.countPending("outbound_orders", "t.status = 'RESERVED'", "t.warehouse_id", filter),
                repository.countPending("transfer_orders", "t.status IN ('REQUESTED', 'APPROVED', 'IN_TRANSIT')", "t.source_warehouse_id", filter),
                last7Days(filter));
    }

    /** Nhập xuất 7 ngày gần nhất; ngày nào không có giao dịch thì điền số 0 để biểu đồ liền mạch. */
    private List<DailyPoint> last7Days(ReportFilter filter) {
        LocalDate today = today();
        Map<LocalDate, DailyPoint> byDay = new HashMap<>();
        repository.dailyMovement(filter, today.minusDays(6).atStartOfDay()).forEach(point -> byDay.put(point.date(), point));

        List<DailyPoint> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            result.add(byDay.getOrDefault(day, new DailyPoint(day, 0, 0)));
        }
        return result;
    }

    // ==================== HÀM HỖ TRỢ ====================

    private ReportFilter filter(Long warehouseId, String keyword) {
        return new ReportFilter(warehouseId, keyword, accessService.restrictTo(warehouseId));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "Ngày bắt đầu không được sau ngày kết thúc");
        }
    }

    private static <T> PageResponse<T> toPage(List<T> rows, long total, Pageable pageable) {
        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }
}
