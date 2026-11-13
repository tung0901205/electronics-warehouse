package com.tung.warehouse.reporting.repository;

import com.tung.warehouse.reporting.dto.AgingRow;
import com.tung.warehouse.reporting.dto.DailyPoint;
import com.tung.warehouse.reporting.dto.LowStockRow;
import com.tung.warehouse.reporting.dto.ProfitRow;
import com.tung.warehouse.reporting.dto.ReportFilter;
import com.tung.warehouse.reporting.dto.StockMovementRow;
import com.tung.warehouse.reporting.dto.StockValueRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Các truy vấn báo cáo bằng SQL thuần: tổng hợp (SUM, GROUP BY) trên nhiều bảng nên viết SQL trực tiếp sẽ rõ ràng
 * và nhanh hơn JPA. Mọi báo cáo đều đọc từ sổ giao dịch hoặc bảng tồn nên số liệu luôn khớp nhau.
 */
@Repository
public class ReportQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== NHẬP – XUẤT – TỒN ====================

    /**
     * Tồn đầu kỳ, nhập, xuất, tồn cuối kỳ của từng biến thể trong từng kho, tính từ sổ giao dịch.
     * Kỹ thuật: SUM(CASE WHEN ...) cho phép tính nhiều chỉ số trong MỘT lần quét bảng.
     * [from, toExclusive) là khoảng thời gian của kỳ báo cáo.
     */
    private static final String MOVEMENT_SQL = """
            SELECT t.warehouse_id, w.code AS warehouse_code, t.variant_id, v.sku, p.name AS product_name,
                   SUM(CASE WHEN t.created_at < ? THEN t.quantity_delta ELSE 0 END) AS opening,
                   SUM(CASE WHEN t.created_at >= ? AND t.created_at < ? AND t.quantity_delta > 0 THEN t.quantity_delta ELSE 0 END) AS qty_in,
                   SUM(CASE WHEN t.created_at >= ? AND t.created_at < ? AND t.quantity_delta < 0 THEN -t.quantity_delta ELSE 0 END) AS qty_out,
                   SUM(t.quantity_delta) AS closing
              FROM inventory_transactions t
              JOIN warehouses w ON w.id = t.warehouse_id
              JOIN product_variants v ON v.id = t.variant_id
              JOIN products p ON p.id = v.product_id
            """;

    private static final String MOVEMENT_GROUP = """
             GROUP BY t.warehouse_id, w.code, t.variant_id, v.sku, p.name
            HAVING NOT (opening = 0 AND qty_in = 0 AND qty_out = 0 AND closing = 0)
            """;

    public List<StockMovementRow> findStockMovement(ReportFilter filter, LocalDateTime from, LocalDateTime toExclusive, long offset, int limit) {
        SqlWhere where = movementWhere(filter, toExclusive);
        String sql = MOVEMENT_SQL + where.sql() + MOVEMENT_GROUP + " ORDER BY p.name, v.sku, w.code LIMIT ? OFFSET ?";
        List<Object> args = movementArgs(from, toExclusive, where);
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new StockMovementRow(
                rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getLong("variant_id"), rs.getString("sku"),
                rs.getString("product_name"), rs.getLong("opening"), rs.getLong("qty_in"), rs.getLong("qty_out"), rs.getLong("closing")),
                args.toArray());
    }

    public long countStockMovement(ReportFilter filter, LocalDateTime from, LocalDateTime toExclusive) {
        SqlWhere where = movementWhere(filter, toExclusive);
        String sql = "SELECT COUNT(*) FROM (" + MOVEMENT_SQL + where.sql() + MOVEMENT_GROUP + ") AS grouped";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, movementArgs(from, toExclusive, where).toArray());
        return total == null ? 0 : total;
    }

    private SqlWhere movementWhere(ReportFilter filter, LocalDateTime toExclusive) {
        SqlWhere where = new SqlWhere().and("t.created_at < ?", toExclusive);
        scope(where, filter, "t.warehouse_id");
        return where;
    }

    /** Thứ tự tham số phải đúng thứ tự dấu ? trong câu SQL: các dấu ? trong SELECT trước, rồi tới WHERE. */
    private List<Object> movementArgs(LocalDateTime from, LocalDateTime toExclusive, SqlWhere where) {
        List<Object> args = new ArrayList<>(List.of(from, from, toExclusive, from, toExclusive));
        args.addAll(where.args());
        return args;
    }

    // ==================== GIÁ TRỊ TỒN ====================

    private static final String VALUE_FROM = """
              FROM inventory_balances b
              JOIN warehouses w ON w.id = b.warehouse_id
              JOIN product_variants v ON v.id = b.variant_id
              JOIN products p ON p.id = v.product_id
              JOIN inventory_batches ib ON ib.id = b.batch_id
            """;

    public List<StockValueRow> findStockValue(ReportFilter filter, long offset, int limit) {
        SqlWhere where = valueWhere(filter);
        String sql = "SELECT b.warehouse_id, w.code AS warehouse_code, b.variant_id, v.sku, p.name AS product_name, "
                + "SUM(b.on_hand) AS on_hand, SUM(b.on_hand * ib.unit_cost) AS value" + VALUE_FROM + where.sql()
                + " GROUP BY b.warehouse_id, w.code, b.variant_id, v.sku, p.name ORDER BY p.name, v.sku, w.code LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new StockValueRow(
                rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getLong("variant_id"), rs.getString("sku"),
                rs.getString("product_name"), rs.getLong("on_hand"), rs.getBigDecimal("value")), args.toArray());
    }

    public long countStockValue(ReportFilter filter) {
        SqlWhere where = valueWhere(filter);
        String sql = "SELECT COUNT(*) FROM (SELECT 1" + VALUE_FROM + where.sql() + " GROUP BY b.warehouse_id, b.variant_id) AS grouped";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, where.args().toArray());
        return total == null ? 0 : total;
    }

    public BigDecimal totalStockValue(ReportFilter filter) {
        SqlWhere where = valueWhere(filter);
        BigDecimal total = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(b.on_hand * ib.unit_cost), 0)" + VALUE_FROM + where.sql(),
                BigDecimal.class, where.args().toArray());
        return total == null ? BigDecimal.ZERO : total;
    }

    private SqlWhere valueWhere(ReportFilter filter) {
        SqlWhere where = new SqlWhere().and("b.on_hand > 0");
        scope(where, filter, "b.warehouse_id");
        return where;
    }

    // ==================== LỢI NHUẬN GỘP ====================

    /** Doanh thu = số lượng phân bổ x giá bán của dòng; giá vốn = số lượng phân bổ x giá vốn của lô. Chỉ tính đơn xuất bán đã hoàn tất. */
    public List<ProfitRow> findGrossProfit(ReportFilter filter, LocalDateTime from, LocalDateTime toExclusive) {
        SqlWhere where = new SqlWhere().and("o.status = 'COMPLETED'").and("o.purpose = 'SALE'")
                .and("o.confirmed_at >= ?", from).and("o.confirmed_at < ?", toExclusive);
        scope(where, filter, "o.warehouse_id");
        String sql = """
                SELECT v.id AS variant_id, v.sku, p.name AS product_name, SUM(a.quantity) AS quantity,
                       SUM(a.quantity * l.unit_price) AS revenue, SUM(a.quantity * a.unit_cost) AS cost
                  FROM outbound_allocations a
                  JOIN outbound_order_lines l ON l.id = a.outbound_line_id
                  JOIN outbound_orders o ON o.id = l.outbound_order_id
                  JOIN product_variants v ON v.id = l.variant_id
                  JOIN products p ON p.id = v.product_id
                """ + where.sql() + " GROUP BY v.id, v.sku, p.name ORDER BY p.name, v.sku";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            BigDecimal revenue = rs.getBigDecimal("revenue");
            BigDecimal cost = rs.getBigDecimal("cost");
            return new ProfitRow(rs.getLong("variant_id"), rs.getString("sku"), rs.getString("product_name"),
                    rs.getLong("quantity"), revenue, cost, revenue.subtract(cost));
        }, where.args().toArray());
    }

    // ==================== TỒN THẤP, TỒN LÂU ====================

    /** Biến thể có tồn khả dụng (cộng mọi vị trí và lô) không vượt ngưỡng. Ít hàng nhất đứng đầu. */
    public List<LowStockRow> findLowStock(ReportFilter filter, int maxQuantity) {
        SqlWhere where = new SqlWhere();
        scope(where, filter, "b.warehouse_id");
        String sql = """
                SELECT b.warehouse_id, w.code AS warehouse_code, b.variant_id, v.sku, p.name AS product_name,
                       SUM(b.on_hand - b.reserved) AS available
                  FROM inventory_balances b
                  JOIN warehouses w ON w.id = b.warehouse_id
                  JOIN product_variants v ON v.id = b.variant_id
                  JOIN products p ON p.id = v.product_id
                """ + where.sql() + " GROUP BY b.warehouse_id, w.code, b.variant_id, v.sku, p.name "
                + "HAVING SUM(b.on_hand - b.reserved) <= ? ORDER BY available ASC, p.name, v.sku LIMIT 200";
        List<Object> args = new ArrayList<>(where.args());
        args.add(maxQuantity);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new LowStockRow(rs.getLong("warehouse_id"), rs.getString("warehouse_code"),
                rs.getLong("variant_id"), rs.getString("sku"), rs.getString("product_name"), rs.getLong("available")), args.toArray());
    }

    /** Các lô còn hàng mà nhận từ trước mốc cutoff; lô cũ nhất đứng đầu. */
    public List<AgingRow> findAging(ReportFilter filter, LocalDateTime cutoff, LocalDateTime now) {
        SqlWhere where = new SqlWhere().and("b.on_hand > 0").and("ib.received_at < ?", cutoff);
        scope(where, filter, "b.warehouse_id");
        String sql = "SELECT w.code AS warehouse_code, v.sku, p.name AS product_name, ib.batch_code, ib.received_at, "
                + "DATEDIFF(?, ib.received_at) AS days_in_stock, b.on_hand, b.on_hand * ib.unit_cost AS value"
                + VALUE_FROM + where.sql() + " ORDER BY ib.received_at ASC LIMIT 200";
        List<Object> args = new ArrayList<>(List.of(now));
        args.addAll(where.args());
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AgingRow(rs.getString("warehouse_code"), rs.getString("sku"),
                rs.getString("product_name"), rs.getString("batch_code"), rs.getObject("received_at", LocalDateTime.class),
                rs.getLong("days_in_stock"), rs.getLong("on_hand"), rs.getBigDecimal("value")), args.toArray());
    }

    // ==================== DASHBOARD ====================

    /** Tổng hợp tồn: số biến thể còn hàng, tổng tồn thực tế, tổng giá trị. */
    public Map<String, Object> stockTotals(ReportFilter filter) {
        SqlWhere where = new SqlWhere().and("b.on_hand > 0");
        scope(where, filter, "b.warehouse_id");
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT b.variant_id) AS variants, COALESCE(SUM(b.on_hand), 0) AS on_hand,
                       COALESCE(SUM(b.on_hand * ib.unit_cost), 0) AS value
                  FROM inventory_balances b JOIN inventory_batches ib ON ib.id = b.batch_id
                """ + where.sql(), where.args().toArray());
    }

    public long countLowStock(ReportFilter filter, int maxQuantity) {
        SqlWhere where = new SqlWhere();
        scope(where, filter, "b.warehouse_id");
        List<Object> args = new ArrayList<>(where.args());
        args.add(maxQuantity);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT 1 FROM inventory_balances b" + where.sql()
                + " GROUP BY b.warehouse_id, b.variant_id HAVING SUM(b.on_hand - b.reserved) <= ?) AS grouped", Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public long countAging(ReportFilter filter, LocalDateTime cutoff) {
        SqlWhere where = new SqlWhere().and("b.on_hand > 0").and("ib.received_at < ?", cutoff);
        scope(where, filter, "b.warehouse_id");
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_balances b JOIN inventory_batches ib ON ib.id = b.batch_id"
                + where.sql(), Long.class, where.args().toArray());
        return total == null ? 0 : total;
    }

    /** Đếm số phiếu đang chờ xử lý: table và statusCondition do code đặt, không phải từ người dùng. */
    public long countPending(String table, String statusCondition, String warehouseColumn, ReportFilter filter) {
        SqlWhere where = new SqlWhere().and(statusCondition);
        scope(where, filter, warehouseColumn);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " t" + where.sql(), Long.class, where.args().toArray());
        return total == null ? 0 : total;
    }

    /** Số lượng nhập và xuất theo từng ngày kể từ mốc since. Ngày không có giao dịch thì không có dòng (service tự bổ sung số 0). */
    public List<DailyPoint> dailyMovement(ReportFilter filter, LocalDateTime since) {
        SqlWhere where = new SqlWhere().and("t.created_at >= ?", since);
        scope(where, filter, "t.warehouse_id");
        return jdbcTemplate.query("""
                SELECT DATE(t.created_at) AS day,
                       SUM(CASE WHEN t.quantity_delta > 0 THEN t.quantity_delta ELSE 0 END) AS qty_in,
                       SUM(CASE WHEN t.quantity_delta < 0 THEN -t.quantity_delta ELSE 0 END) AS qty_out
                  FROM inventory_transactions t
                """ + where.sql() + " GROUP BY DATE(t.created_at) ORDER BY day",
                (rs, rowNum) -> new DailyPoint(rs.getObject("day", LocalDate.class), rs.getLong("qty_in"), rs.getLong("qty_out")),
                where.args().toArray());
    }

    // ==================== DÙNG CHUNG ====================

    /** Thêm điều kiện kho, từ khóa và phạm vi kho được xem. Điều kiện nào rỗng thì bỏ qua. */
    private void scope(SqlWhere where, ReportFilter filter, String warehouseColumn) {
        if (filter.warehouseId() != null) {
            where.and(warehouseColumn + " = ?", filter.warehouseId());
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
            where.and("(LOWER(v.sku) LIKE ? OR LOWER(p.name) LIKE ?)", pattern, pattern);
        }
        if (filter.restrictTo() != null) {
            if (filter.restrictTo().isEmpty()) {
                where.and("1 = 0"); // không được gán kho nào: không thấy dòng nào
            } else {
                where.and(warehouseColumn + " IN (" + SqlWhere.placeholders(filter.restrictTo().size()) + ")", filter.restrictTo().toArray());
            }
        }
    }
}
