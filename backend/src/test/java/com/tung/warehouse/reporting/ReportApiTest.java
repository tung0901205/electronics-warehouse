package com.tung.warehouse.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.catalog.entity.TrackingType;
import com.tung.warehouse.inventory.dto.ReceiveCommand;
import com.tung.warehouse.inventory.dto.ReceiveResult;
import com.tung.warehouse.inventory.dto.StockOutCommand;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.support.AbstractApiTest;
import com.tung.warehouse.support.InventoryFixtures;
import com.tung.warehouse.support.InventoryFixtures.TestVariant;
import com.tung.warehouse.support.InventoryFixtures.TestWarehouse;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test báo cáo, dashboard, xuất CSV và nhật ký thao tác. Điểm chính: số liệu báo cáo phải khớp sổ giao dịch và tồn kho. */
class ReportApiTest extends AbstractApiTest {

    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        fixtures.cleanUpAll();
    }

    /** Nhập 10 sản phẩm giá vốn 250 rồi xuất 3: tồn còn 7. */
    private TestVariant stockedVariant() {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult received = inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), "LOT1",
                null, null, new BigDecimal("250"), 12, 10, null, "TEST", 1L, "Nhập thử"));
        inventoryService.decrease(new StockOutCommand(received.balanceId(), 3, TransactionType.OUTBOUND, false, null, "TEST", 2L, "Xuất thử"));
        return v;
    }

    private String get(String path, String user) throws Exception {
        return call("GET", path, null, loginAndGetAccessToken(user)).body();
    }

    @Test
    @DisplayName("Nhập–xuất–tồn: đầu kỳ, nhập, xuất, cuối kỳ khớp sổ giao dịch; kỳ trong tương lai thì đầu kỳ bằng tồn hiện tại")
    void stockMovementMatchesLedger() throws Exception {
        TestVariant v = stockedVariant();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        String base = "/api/reports/stock-movement?keyword=" + v.sku().toLowerCase();

        String thisPeriod = get(base + "&from=" + today + "&to=" + today, "viewer");
        assertThat(thisPeriod).contains("\"opening\":0,\"qtyIn\":10,\"qtyOut\":3,\"closing\":7");

        String nextPeriod = get(base + "&from=" + tomorrow + "&to=" + tomorrow, "viewer");
        assertThat(nextPeriod).contains("\"opening\":7,\"qtyIn\":0,\"qtyOut\":0,\"closing\":7");

        // Đối chiếu với bảng tồn
        Integer onHand = jdbcTemplate.queryForObject("select sum(on_hand) from inventory_balances where variant_id = ?", Integer.class, v.variantId());
        assertThat(onHand).isEqualTo(7);

        assertThat(call("GET", base + "&from=" + tomorrow + "&to=" + today, null, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Giá trị tồn và lợi nhuận chỉ cho Quản lý kho; tồn thấp, tồn lâu; xuất CSV; dashboard ẩn giá trị với Người xem")
    void valueLowStockAgingExportAndDashboard() throws Exception {
        TestVariant v = stockedVariant();
        String sku = v.sku().toLowerCase();

        String value = get("/api/reports/stock-value?keyword=" + sku, "manager");
        assertThat(value).contains("\"totalValue\":1750");                                    // 7 sản phẩm x giá vốn 250
        assertThat(call("GET", "/api/reports/stock-value", null, loginAndGetAccessToken("staff")).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/reports/gross-profit", null, loginAndGetAccessToken("manager")).statusCode()).isEqualTo(200);

        assertThat(get("/api/reports/low-stock?maxQuantity=10", "viewer")).contains(v.sku());
        assertThat(get("/api/reports/low-stock?maxQuantity=5", "viewer")).doesNotContain(v.sku());

        jdbcTemplate.update("update inventory_batches set received_at = date_sub(utc_timestamp(), interval 100 day) where variant_id = ?", v.variantId());
        assertThat(get("/api/reports/aging?days=90", "viewer")).contains(v.sku());
        assertThat(get("/api/reports/aging?days=200", "viewer")).doesNotContain(v.sku());

        HttpResponse<String> csv = call("GET", "/api/reports/stock-movement/export?keyword=" + sku, null, loginAndGetAccessToken("viewer"));
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains("warehouse,sku,product,opening,in,out,closing").contains(v.sku());

        assertThat(get("/api/dashboard", "manager")).doesNotContain("\"totalValue\":null").contains("\"last7Days\"");
        assertThat(get("/api/dashboard", "viewer")).contains("\"totalValue\":null");
    }

    @Test
    @DisplayName("Nhật ký: đăng nhập thất bại vẫn được ghi (transaction riêng); chỉ Admin xem được")
    void auditLog() throws Exception {
        String username = "khong-co-" + InventoryFixtures.random().toLowerCase();
        assertThat(login(username, "sai-mat-khau").statusCode()).isEqualTo(401);

        String failures = get("/api/audit-logs?action=LOGIN_FAILURE", "admin");
        assertThat(failures).contains(username).contains("\"result\":\"FAILURE\"");
        assertThat(get("/api/audit-logs?action=LOGIN_SUCCESS", "admin")).contains("\"action\":\"LOGIN_SUCCESS\"");
        assertThat(call("GET", "/api/audit-logs", null, loginAndGetAccessToken("manager")).statusCode()).isEqualTo(403);
    }
}
