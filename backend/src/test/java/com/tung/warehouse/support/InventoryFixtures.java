package com.tung.warehouse.support;

import com.tung.warehouse.catalog.dto.CategoryRequest;
import com.tung.warehouse.catalog.dto.ProductRequest;
import com.tung.warehouse.catalog.dto.VariantRequest;
import com.tung.warehouse.catalog.entity.TrackingType;
import com.tung.warehouse.catalog.service.CategoryService;
import com.tung.warehouse.catalog.service.ProductService;
import com.tung.warehouse.catalog.service.UnitService;
import com.tung.warehouse.warehouse.dto.LocationRequest;
import com.tung.warehouse.warehouse.dto.WarehouseRequest;
import com.tung.warehouse.warehouse.dto.ZoneRequest;
import com.tung.warehouse.warehouse.service.LocationService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import com.tung.warehouse.warehouse.service.ZoneService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dựng sẵn dữ liệu cho các test tồn kho (kho, vị trí, sản phẩm, biến thể) và dọn sau test.
 * Chỉ nằm trong thư mục test. Mọi dữ liệu tạo ra đều có tiền tố TSTINV để dọn được mà không đụng dữ liệu khác.
 */
@Component
public class InventoryFixtures {

    public record TestWarehouse(long warehouseId, String code, long locationId) {
    }

    public record TestVariant(long variantId, String sku) {
    }

    private final WarehouseService warehouseService;
    private final ZoneService zoneService;
    private final LocationService locationService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final UnitService unitService;
    private final JdbcTemplate jdbcTemplate;

    public InventoryFixtures(WarehouseService warehouseService, ZoneService zoneService, LocationService locationService,
                             CategoryService categoryService, ProductService productService, UnitService unitService,
                             JdbcTemplate jdbcTemplate) {
        this.warehouseService = warehouseService;
        this.zoneService = zoneService;
        this.locationService = locationService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.unitService = unitService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** Tạo một kho mới có một khu vực và một vị trí thường. */
    public TestWarehouse createWarehouse() {
        String code = "TSTINV" + random();
        long warehouseId = warehouseService.create(new WarehouseRequest(code, "Kho test " + code, "Test")).id();
        long zoneId = zoneService.create(warehouseId, new ZoneRequest("A", "Khu A")).id();
        long locationId = locationService.create(zoneId, new LocationRequest("A1-T1")).id();
        return new TestWarehouse(warehouseId, code, locationId);
    }

    /** Dùng kho mẫu KHO-HN và vị trí A1-T1 do migration V6 tạo sẵn (các tài khoản demo được gán kho này). */
    public TestWarehouse seededWarehouse() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT w.id AS warehouse_id, l.id AS location_id
                  FROM warehouses w
                  JOIN warehouse_zones z ON z.warehouse_id = w.id
                  JOIN storage_locations l ON l.zone_id = z.id
                 WHERE w.code = 'KHO-HN' AND l.code = 'A1-T1'
                """);
        return new TestWarehouse(((Number) row.get("warehouse_id")).longValue(), "KHO-HN",
                ((Number) row.get("location_id")).longValue());
    }

    /** Id vị trí hàng lỗi (DEFECTIVE) của một kho. */
    public long defectiveLocationId(long warehouseId) {
        Long id = jdbcTemplate.queryForObject("""
                SELECT l.id FROM storage_locations l JOIN warehouse_zones z ON z.id = l.zone_id
                 WHERE z.warehouse_id = ? AND l.location_type = 'DEFECTIVE'
                """, Long.class, warehouseId);
        return id == null ? 0 : id;
    }

    /** Tạo danh mục, sản phẩm và một biến thể mới với kiểu theo dõi cho trước. */
    public TestVariant createVariant(TrackingType trackingType) {
        String r = random();
        long categoryId = categoryService.create(new CategoryRequest("TSTINV danh mục " + r, null)).id();
        long productId = productService.create(new ProductRequest("TSTINV sản phẩm " + r, categoryId, null, null)).id();
        long unitId = unitService.findAll().get(0).id();
        String sku = "TSTINV-" + r;
        long variantId = productService.addVariant(productId,
                new VariantRequest(sku, null, null, unitId, trackingType, new BigDecimal("1000"), 12)).id();
        return new TestVariant(variantId, sku);
    }

    /**
     * [MỚI Giai đoạn 6] Dọn MỌI dữ liệu test liên quan tới biến thể và kho TSTINV: đổi trả, kiểm kê, điều chuyển, xuất kho,
     * sự kiện serial, rồi gọi cleanUp() để dọn phần tồn kho. Thứ tự đi từ bảng "con" lên bảng "cha" theo khóa ngoại.
     */
    public void cleanUpAll() {
        String variants = "(select id from product_variants where sku like 'TSTINV%')";
        String warehouses = "(select id from warehouses where code like 'TSTINV%')";
        String balances = "(select id from inventory_balances where variant_id in " + variants + ")";

        // đổi trả
        jdbcTemplate.update("delete from rma_lines where variant_id in " + variants);
        jdbcTemplate.update("delete from rma_orders where outbound_order_id in "
                + "(select outbound_order_id from outbound_order_lines where variant_id in " + variants + ")");

        // kiểm kê
        jdbcTemplate.update("delete from stock_adjustments where stocktake_line_id in "
                + "(select id from stocktake_lines where balance_id in " + balances + ")");
        jdbcTemplate.update("delete from stocktake_lines where balance_id in " + balances);
        jdbcTemplate.update("delete from stocktake_sessions where scope_variant_id in " + variants + " or warehouse_id in " + warehouses);

        // điều chuyển
        List<Long> transferIds = jdbcTemplate.queryForList(
                "select distinct transfer_order_id from transfer_order_lines where variant_id in " + variants, Long.class);
        jdbcTemplate.update("delete from transfer_unit_items where transfer_line_id in "
                + "(select id from transfer_order_lines where variant_id in " + variants + ")");
        jdbcTemplate.update("delete from transfer_order_lines where variant_id in " + variants);
        if (!transferIds.isEmpty()) {
            jdbcTemplate.update("delete from transfer_orders where id in (" + join(transferIds) + ")");
        }

        // xuất kho
        String lines = "(select id from outbound_order_lines where variant_id in " + variants + ")";
        List<Long> outboundIds = jdbcTemplate.queryForList(
                "select distinct outbound_order_id from outbound_order_lines where variant_id in " + variants, Long.class);
        jdbcTemplate.update("delete from outbound_allocations where outbound_line_id in " + lines);
        jdbcTemplate.update("delete from inventory_reservations where outbound_line_id in " + lines);
        jdbcTemplate.update("delete from outbound_order_lines where variant_id in " + variants);
        if (!outboundIds.isEmpty()) {
            jdbcTemplate.update("delete from outbound_orders where id in (" + join(outboundIds) + ")");
        }

        // vòng đời serial
        jdbcTemplate.update("delete from device_unit_events where device_unit_id in "
                + "(select id from device_units where variant_id in " + variants + ")");

        cleanUp();
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Dọn theo đúng thứ tự khóa ngoại: sổ giao dịch, thiết bị, tồn, lô, rồi tới biến thể, sản phẩm, danh mục, kho. */
    public void cleanUp() {
        String variants = "(select id from product_variants where sku like 'TSTINV%')";
        jdbcTemplate.update("delete from inventory_transactions where variant_id in " + variants);
        jdbcTemplate.update("delete from device_units where variant_id in " + variants);
        jdbcTemplate.update("delete from inventory_balances where variant_id in " + variants);
        jdbcTemplate.update("delete from inventory_batches where variant_id in " + variants);
        jdbcTemplate.update("delete from product_variants where sku like 'TSTINV%'");
        jdbcTemplate.update("delete from products where name like 'TSTINV%'");
        jdbcTemplate.update("delete from categories where name like 'TSTINV%'");

        String warehouses = "(select id from warehouses where code like 'TSTINV%')";
        jdbcTemplate.update("delete from storage_locations where zone_id in "
                + "(select id from warehouse_zones where warehouse_id in " + warehouses + ")");
        jdbcTemplate.update("delete from warehouse_zones where warehouse_id in " + warehouses);
        jdbcTemplate.update("delete from warehouses where code like 'TSTINV%'");
    }
}
