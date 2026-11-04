package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.dto.BalanceRow;
import com.tung.warehouse.inventory.dto.StockFilter;
import com.tung.warehouse.inventory.dto.SerialRow;
import com.tung.warehouse.inventory.dto.SummaryRow;
import com.tung.warehouse.inventory.dto.TransactionFilter;
import com.tung.warehouse.inventory.dto.TransactionRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Các truy vấn ĐỌC cho màn hình tồn kho, viết bằng SQL thuần qua JdbcTemplate.
 * Vì sao không dùng JPA ở đây: mỗi dòng kết quả cần ghép dữ liệu của nhiều module
 * (kho, vị trí, sản phẩm, lô...). SQL JOIN làm việc này đơn giản và nhanh,
 * trong khi JPA sẽ bắt module inventory phải import entity của các module khác.
 */
@Repository
public class InventoryQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public InventoryQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== TỒN THEO TỪNG DÒNG (kho, vị trí, lô) ====================

    private static final String BALANCE_FROM = """
             FROM inventory_balances b
             JOIN warehouses w ON w.id = b.warehouse_id
             JOIN storage_locations l ON l.id = b.location_id
             JOIN product_variants v ON v.id = b.variant_id
             JOIN products p ON p.id = v.product_id
             JOIN inventory_batches ib ON ib.id = b.batch_id
            """;

    public List<BalanceRow> findBalances(StockFilter filter, long offset, int limit) {
        SqlWhere where = balanceWhere(filter);
        String sql = """
                SELECT b.id, b.warehouse_id, w.code AS warehouse_code, b.location_id, l.code AS location_code,
                       b.variant_id, v.sku, p.name AS product_name, v.attributes, v.tracking_type,
                       b.batch_id, ib.batch_code, ib.received_at, ib.unit_cost, ib.warranty_months,
                       b.on_hand, b.reserved, (b.on_hand - b.reserved) AS available
                """ + BALANCE_FROM + where.sql() + " ORDER BY ib.received_at ASC, b.id ASC LIMIT ? OFFSET ?";

        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new BalanceRow(
                rs.getLong("id"),
                rs.getLong("warehouse_id"),
                rs.getString("warehouse_code"),
                rs.getLong("location_id"),
                rs.getString("location_code"),
                rs.getLong("variant_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("attributes"),
                rs.getString("tracking_type"),
                rs.getLong("batch_id"),
                rs.getString("batch_code"),
                rs.getObject("received_at", LocalDateTime.class),
                rs.getBigDecimal("unit_cost"),
                rs.getInt("warranty_months"),
                rs.getInt("on_hand"),
                rs.getInt("reserved"),
                rs.getInt("available")), args.toArray());
    }

    public long countBalances(StockFilter filter) {
        SqlWhere where = balanceWhere(filter);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + BALANCE_FROM + where.sql(), Long.class, where.args().toArray());
        return total == null ? 0 : total;
    }

    // ==================== TỒN CỘNG DỒN THEO BIẾN THỂ ====================

    private static final String SUMMARY_FROM = """
             FROM inventory_balances b
             JOIN warehouses w ON w.id = b.warehouse_id
             JOIN storage_locations l ON l.id = b.location_id
             JOIN product_variants v ON v.id = b.variant_id
             JOIN products p ON p.id = v.product_id
            """;

    private static final String SUMMARY_GROUP = " GROUP BY b.warehouse_id, w.code, b.variant_id, v.sku, p.name, v.tracking_type";

    public List<SummaryRow> findSummary(StockFilter filter, long offset, int limit) {
        SqlWhere where = balanceWhere(filter); // dùng lại điều kiện của dòng tồn
        String sql = """
                SELECT b.warehouse_id, w.code AS warehouse_code, b.variant_id, v.sku, p.name AS product_name,
                       v.tracking_type, SUM(b.on_hand) AS on_hand, SUM(b.reserved) AS reserved,
                       SUM(b.on_hand - b.reserved) AS available
                """ + SUMMARY_FROM + where.sql() + SUMMARY_GROUP + " ORDER BY p.name ASC, v.sku ASC LIMIT ? OFFSET ?";

        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SummaryRow(
                rs.getLong("warehouse_id"),
                rs.getString("warehouse_code"),
                rs.getLong("variant_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("tracking_type"),
                rs.getLong("on_hand"),
                rs.getLong("reserved"),
                rs.getLong("available")), args.toArray());
    }

    public long countSummary(StockFilter filter) {
        SqlWhere where = balanceWhere(filter);
        // Đếm số nhóm: bọc câu GROUP BY trong một truy vấn con rồi COUNT
        String sql = "SELECT COUNT(*) FROM (SELECT 1" + SUMMARY_FROM + where.sql() + SUMMARY_GROUP + ") AS grouped";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, where.args().toArray());
        return total == null ? 0 : total;
    }

    /** Ghép điều kiện cho dòng tồn. Điều kiện nào không có giá trị thì bỏ qua. */
    private SqlWhere balanceWhere(StockFilter filter) {
        SqlWhere where = new SqlWhere();
        if (filter.warehouseId() != null) {
            where.and("b.warehouse_id = ?", filter.warehouseId());
        }
        if (filter.locationId() != null) {
            where.and("b.location_id = ?", filter.locationId());
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
            where.and("(LOWER(v.sku) LIKE ? OR LOWER(p.name) LIKE ?)", pattern, pattern);
        }
        if (!filter.includeZero()) {
            where.and("b.on_hand > 0"); // mặc định ẩn các dòng đã hết hàng
        }
        if (filter.restrictTo() != null) {
            where.and("b.warehouse_id IN (" + SqlWhere.placeholders(filter.restrictTo().size()) + ")",
                    filter.restrictTo().toArray());
        }
        return where;
    }

    // ==================== SỔ GIAO DỊCH ====================

    private static final String TRANSACTION_FROM = """
             FROM inventory_transactions t
             JOIN warehouses w ON w.id = t.warehouse_id
             JOIN storage_locations l ON l.id = t.location_id
             JOIN product_variants v ON v.id = t.variant_id
             JOIN products p ON p.id = v.product_id
             JOIN inventory_batches ib ON ib.id = t.batch_id
             LEFT JOIN users u ON u.id = t.performed_by
            """;

    public List<TransactionRow> findTransactions(TransactionFilter filter, long offset, int limit) {
        SqlWhere where = transactionWhere(filter);
        String sql = """
                SELECT t.id, t.created_at, t.type, t.warehouse_id, w.code AS warehouse_code, t.location_id,
                       l.code AS location_code, t.variant_id, v.sku, p.name AS product_name, t.batch_id,
                       ib.batch_code, t.quantity_delta, t.unit_cost, t.reference_type, t.reference_id, t.note,
                       t.performed_by, u.username AS performed_by_username
                """ + TRANSACTION_FROM + where.sql() + " ORDER BY t.id DESC LIMIT ? OFFSET ?";

        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TransactionRow(
                rs.getLong("id"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getString("type"),
                rs.getLong("warehouse_id"),
                rs.getString("warehouse_code"),
                rs.getLong("location_id"),
                rs.getString("location_code"),
                rs.getLong("variant_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getLong("batch_id"),
                rs.getString("batch_code"),
                rs.getInt("quantity_delta"),
                rs.getBigDecimal("unit_cost"),
                rs.getString("reference_type"),
                rs.getLong("reference_id"),
                rs.getString("note"),
                rs.getObject("performed_by", Long.class),   // có thể null
                rs.getString("performed_by_username")), args.toArray());
    }

    public long countTransactions(TransactionFilter filter) {
        SqlWhere where = transactionWhere(filter);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + TRANSACTION_FROM + where.sql(), Long.class, where.args().toArray());
        return total == null ? 0 : total;
    }

    private SqlWhere transactionWhere(TransactionFilter filter) {
        SqlWhere where = new SqlWhere();
        if (filter.warehouseId() != null) {
            where.and("t.warehouse_id = ?", filter.warehouseId());
        }
        if (filter.variantId() != null) {
            where.and("t.variant_id = ?", filter.variantId());
        }
        if (filter.type() != null) {
            where.and("t.type = ?", filter.type().name());
        }
        if (filter.referenceType() != null && !filter.referenceType().isBlank()) {
            where.and("t.reference_type = ?", filter.referenceType().trim());
        }
        if (filter.referenceId() != null) {
            where.and("t.reference_id = ?", filter.referenceId());
        }
        if (filter.restrictTo() != null) {
            where.and("t.warehouse_id IN (" + SqlWhere.placeholders(filter.restrictTo().size()) + ")",
                    filter.restrictTo().toArray());
        }
        return where;
    }

    // ==================== TRA CỨU THEO SERIAL ====================

    /**
     * Tìm thiết bị theo số serial. Thiết bị đã rời kho (đã bán, đang vận chuyển) không có vị trí,
     * nên dùng LEFT JOIN để vẫn thấy nó. restrictTo: chỉ lọc các thiết bị đang nằm ở kho trong danh sách
     * (thiết bị không còn ở kho nào vẫn được xem).
     */
    public List<SerialRow> findBySerial(String serial, List<Long> restrictTo) {
        SqlWhere where = new SqlWhere().and("d.serial_number = ?", serial);
        if (restrictTo != null && restrictTo.isEmpty()) {
            where.and("z.warehouse_id IS NULL"); // không được gán kho nào: chỉ xem được thiết bị đã rời kho
        } else if (restrictTo != null) {
            where.and("(z.warehouse_id IS NULL OR z.warehouse_id IN (" + SqlWhere.placeholders(restrictTo.size()) + "))",
                    restrictTo.toArray());
        }
        String sql = """
                SELECT d.id, d.serial_number, d.status, d.warranty_end_date, d.variant_id, v.sku, p.name AS product_name,
                       d.batch_id, ib.batch_code, ib.received_at, d.location_id, l.code AS location_code,
                       z.warehouse_id, w.code AS warehouse_code
                  FROM device_units d
                  JOIN product_variants v ON v.id = d.variant_id
                  JOIN products p ON p.id = v.product_id
                  JOIN inventory_batches ib ON ib.id = d.batch_id
                  LEFT JOIN storage_locations l ON l.id = d.location_id
                  LEFT JOIN warehouse_zones z ON z.id = l.zone_id
                  LEFT JOIN warehouses w ON w.id = z.warehouse_id
                """ + where.sql() + " ORDER BY d.id";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SerialRow(
                rs.getLong("id"),
                rs.getString("serial_number"),
                rs.getString("status"),
                rs.getObject("warranty_end_date", LocalDate.class),
                rs.getLong("variant_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getLong("batch_id"),
                rs.getString("batch_code"),
                rs.getObject("received_at", LocalDateTime.class),
                rs.getObject("location_id", Long.class),
                rs.getString("location_code"),
                rs.getObject("warehouse_id", Long.class),
                rs.getString("warehouse_code")), where.args().toArray());
    }
}
