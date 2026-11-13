package com.tung.warehouse.stocktake;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.catalog.entity.TrackingType;
import com.tung.warehouse.inventory.dto.ReceiveCommand;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.support.AbstractApiTest;
import com.tung.warehouse.support.InventoryFixtures;
import com.tung.warehouse.support.InventoryFixtures.TestVariant;
import com.tung.warehouse.support.InventoryFixtures.TestWarehouse;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test kiểm kê (UC6.1 đến UC6.3): điều chỉnh tồn qua giao dịch ADJUST, người duyệt khác người đếm, phát hiện tồn thay đổi. */
class StocktakeApiTest extends AbstractApiTest {

    private static final String URL = "/api/stocktakes";

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

    /** Tạo đợt kiểm kê theo sản phẩm, bắt đầu đếm và trả về id của dòng kiểm kê đầu tiên. */
    private long[] startStocktake(TestWarehouse w, TestVariant v, String manager) throws Exception {
        HttpResponse<String> created = call("POST", URL, "{\"warehouseId\":" + w.warehouseId() + ",\"scope\":\"PRODUCT\",\"variantId\":" + v.variantId() + "}", manager);
        assertThat(created.statusCode()).isEqualTo(201);
        long id = number(created.body(), "id");
        HttpResponse<String> started = call("POST", URL + "/" + id + "/start", null, manager);
        assertThat(field(started.body(), "status")).isEqualTo("COUNTING");
        Matcher m = Pattern.compile("\"lines\":\\[\\{\"id\":(\\d+)").matcher(started.body());
        assertThat(m.find()).isTrue();
        return new long[] {id, Long.parseLong(m.group(1))};
    }

    private void count(String token, long id, long lineId, int counted, String reason) throws Exception {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        String body = "{\"entries\":[{\"lineId\":" + lineId + ",\"countedQuantity\":" + counted + ",\"reason\":" + reasonJson + "}]}";
        assertThat(call("PUT", URL + "/" + id + "/counts", body, token).statusCode()).isEqualTo(200);
    }

    private int onHand(TestVariant v) {
        return jdbcTemplate.queryForObject("select coalesce(sum(on_hand), 0) from inventory_balances where variant_id = ?", Integer.class, v.variantId());
    }

    private TestVariant stockedVariant(TestWarehouse w) {
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), "LOT1", null, null,
                new BigDecimal("100"), 12, 10, null, "TEST", 1L, "Nhập thử"));
        return v;
    }

    @Test
    @DisplayName("Luồng đầy đủ: chụp số hệ thống, đếm thiếu 2, nộp, Quản lý duyệt thì tồn về 8 và có giao dịch ADJUST")
    void fullFlow() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant v = stockedVariant(w);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long[] ids = startStocktake(w, v, manager);

        assertThat(call("POST", URL, "{\"warehouseId\":" + w.warehouseId() + ",\"scope\":\"PRODUCT\"}", manager).statusCode()).isEqualTo(400); // thiếu biến thể
        count(staff, ids[0], ids[1], 8, "Mất hàng");
        assertThat(field(call("POST", URL + "/" + ids[0] + "/submit", null, staff).body(), "status")).isEqualTo("PENDING_APPROVAL");

        assertThat(call("POST", URL + "/" + ids[0] + "/apply", null, staff).statusCode()).isEqualTo(403);   // chỉ Quản lý kho được duyệt
        HttpResponse<String> applied = call("POST", URL + "/" + ids[0] + "/apply", null, manager);
        assertThat(field(applied.body(), "status")).isEqualTo("APPLIED");

        assertThat(onHand(v)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("select quantity_delta from inventory_transactions where type = 'ADJUST' and variant_id = ?", Integer.class, v.variantId())).isEqualTo(-2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from stock_adjustments where stocktake_line_id = ?", Integer.class, ids[1])).isEqualTo(1);
        assertThat(call("POST", URL + "/" + ids[0] + "/apply", null, manager).statusCode()).isEqualTo(409);  // không áp dụng lần hai
    }

    @Test
    @DisplayName("Quy tắc: thiếu lý do hoặc chưa đếm thì không nộp được; người đếm không tự duyệt; tồn đổi trong lúc đếm thì phải chụp lại")
    void rules() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant v = stockedVariant(w);
        String manager = loginAndGetAccessToken("manager");
        long[] ids = startStocktake(w, v, manager);
        String submit = URL + "/" + ids[0] + "/submit";

        assertThat(call("POST", submit, null, manager).statusCode()).isEqualTo(400);        // còn dòng chưa đếm
        count(manager, ids[0], ids[1], 7, null);
        assertThat(call("POST", submit, null, manager).statusCode()).isEqualTo(400);        // có chênh lệch mà chưa có lý do
        count(manager, ids[0], ids[1], 7, "Đếm thiếu");
        assertThat(call("POST", submit, null, manager).statusCode()).isEqualTo(200);

        // Người đếm cũng là người duyệt: bị từ chối
        assertThat(call("POST", URL + "/" + ids[0] + "/apply", null, manager).statusCode()).isEqualTo(409);

        // Tồn thay đổi trong lúc đếm (giữ chỗ làm đổi phiên bản dòng tồn): người khác duyệt cũng bị từ chối
        long balanceId = jdbcTemplate.queryForObject("select id from inventory_balances where variant_id = ?", Long.class, v.variantId());
        inventoryService.reserve(balanceId, 1);
        HttpResponse<String> admin = call("POST", URL + "/" + ids[0] + "/apply", null, loginAndGetAccessToken("admin"));
        assertThat(admin.statusCode()).isEqualTo(409);
        assertThat(admin.body()).contains("đếm lại");
        assertThat(onHand(v)).isEqualTo(10);                                                // chưa điều chỉnh gì

        // Chụp lại để đếm lại
        HttpResponse<String> restarted = call("POST", URL + "/" + ids[0] + "/restart", null, manager);
        assertThat(field(restarted.body(), "status")).isEqualTo("COUNTING");
        assertThat(restarted.body()).contains("\"countedQuantity\":null");
    }
}
