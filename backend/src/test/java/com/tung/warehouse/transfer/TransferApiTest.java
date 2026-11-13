package com.tung.warehouse.transfer;

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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test điều chuyển kho (UC5.1 đến UC5.5): luồng đầy đủ, nhận thiếu, tách lô theo FIFO, phân quyền, hoàn tác khi thiếu hàng. */
class TransferApiTest extends AbstractApiTest {

    private static final String URL = "/api/transfer-orders";

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

    private void receive(TestWarehouse w, TestVariant v, String batch, int qty, String cost, List<String> serials) {
        inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), batch, null, null,
                new BigDecimal(cost), 12, qty, serials, "TEST", 1L, "Nhập thử"));
    }

    private static String body(long source, long dest, String... lines) {
        return "{\"sourceWarehouseId\":" + source + ",\"destWarehouseId\":" + dest + ",\"note\":\"Chuyển thử\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    private static String line(TestVariant v, int quantity) {
        return "{\"variantId\":" + v.variantId() + ",\"quantity\":" + quantity + "}";
    }

    private HttpResponse<String> post(String path, String token) throws Exception {
        return call("POST", URL + path, null, token);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private int stock(TestVariant v, long warehouseId) {
        return count("select coalesce(sum(on_hand), 0) from inventory_balances where variant_id = ? and warehouse_id = ?", v.variantId(), warehouseId);
    }

    @Test
    @DisplayName("Luồng đầy đủ: nộp, duyệt, xuất kho nguồn (FIFO, tách lô), nhận thiếu ở kho đích; nhận lần hai bị từ chối")
    void fullFlow() throws Exception {
        TestWarehouse source = fixtures.seededWarehouse();
        TestWarehouse dest = fixtures.createWarehouse();
        TestVariant quantity = fixtures.createVariant(TrackingType.QUANTITY);
        TestVariant serial = fixtures.createVariant(TrackingType.SERIAL);
        String r = InventoryFixtures.random();
        receive(source, quantity, "LOT1", 3, "100", null);
        receive(source, quantity, "LOT2", 5, "120", null);
        receive(source, serial, "LOTS", 2, "1000", List.of("S1" + r, "S2" + r));
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        String admin = loginAndGetAccessToken("admin");

        HttpResponse<String> created = call("POST", URL, body(source.warehouseId(), dest.warehouseId(), line(quantity, 6), line(serial, 2)), staff);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(field(created.body(), "code")).startsWith("DC-");
        long id = number(created.body(), "id");

        assertThat(field(post("/" + id + "/submit", staff).body(), "status")).isEqualTo("REQUESTED");
        assertThat(post("/" + id + "/approve", staff).statusCode()).isEqualTo(403);          // chỉ Quản lý kho được duyệt
        assertThat(field(post("/" + id + "/approve", manager).body(), "status")).isEqualTo("APPROVED");
        assertThat(stock(quantity, source.warehouseId())).isEqualTo(8);                      // duyệt chưa đổi tồn

        // Xuất kho nguồn: lấy hết lô cũ (3) rồi lấy 3 từ lô sau; dòng được tách thành nhiều dòng theo lô
        HttpResponse<String> shipped = post("/" + id + "/ship", staff);
        assertThat(field(shipped.body(), "status")).isEqualTo("IN_TRANSIT");
        assertThat(stock(quantity, source.warehouseId())).isEqualTo(2);
        assertThat(stock(serial, source.warehouseId())).isZero();
        assertThat(count("select count(*) from device_units where status = 'IN_TRANSIT' and variant_id = ?", serial.variantId())).isEqualTo(2);
        assertThat(count("select count(*) from transfer_order_lines where transfer_order_id = ?", id)).isEqualTo(3);
        assertThat(stock(quantity, dest.warehouseId())).isZero();                            // hàng đang vận chuyển chưa thuộc kho đích

        // Nhận hàng: dòng số lượng đầu tiên nhận thiếu 1
        List<String> receiveLines = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\"id\":(\\d+),\"variantId\":\\d+,\"sku\":\"[^\"]*\",\"trackingType\":\"(\\w+)\",\"batchId\":\\d+,\"quantity\":(\\d+)").matcher(shipped.body());
        boolean first = true;
        while (m.find()) {
            long lineId = Long.parseLong(m.group(1));
            int qty = Integer.parseInt(m.group(3));
            int received = first && m.group(2).equals("QUANTITY") ? qty - 1 : qty;
            first = false;
            String serials = m.group(2).equals("SERIAL")
                    ? jdbcTemplate.queryForList("select serial_number from transfer_unit_items where transfer_line_id = ?", String.class, lineId)
                            .stream().map(s -> "\"" + s + "\"").toList().toString() : "[]";
            receiveLines.add("{\"lineId\":" + lineId + ",\"receivedQuantity\":" + received + ",\"destLocationId\":" + dest.locationId() + ",\"serials\":" + serials + "}");
        }
        String receiveBody = "{\"lines\":[" + String.join(",", receiveLines) + "]}";

        HttpResponse<String> denied = call("POST", URL + "/" + id + "/receive", receiveBody, staff);   // staff không được gán kho đích
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("WAREHOUSE_ACCESS_DENIED");

        HttpResponse<String> received = call("POST", URL + "/" + id + "/receive", receiveBody, admin);
        assertThat(field(received.body(), "status")).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(stock(quantity, dest.warehouseId())).isEqualTo(5);                        // 6 xuất, nhận 5
        assertThat(stock(serial, dest.warehouseId())).isEqualTo(2);
        assertThat(count("select count(*) from device_units where status = 'IN_STOCK' and location_id = ? and variant_id = ?",
                dest.locationId(), serial.variantId())).isEqualTo(2);
        assertThat(count("select count(*) from inventory_transactions where type = 'TRANSFER_IN' and reference_id = ?", id)).isEqualTo(3);
        assertThat(call("POST", URL + "/" + id + "/receive", receiveBody, admin).statusCode()).isEqualTo(409);   // không nhận lần hai

        assertThat(call("GET", URL + "/" + id, null, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Quy tắc: hai kho phải khác nhau, từ chối cần lý do, thiếu hàng khi xuất kho nguồn thì hoàn tác, đã xuất thì không hủy được")
    void rules() throws Exception {
        TestWarehouse source = fixtures.seededWarehouse();
        TestWarehouse dest = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        receive(source, v, "LOT1", 4, "100", null);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");

        assertThat(call("POST", URL, body(source.warehouseId(), source.warehouseId(), line(v, 1)), staff).statusCode()).isEqualTo(400);

        long rejected = number(call("POST", URL, body(source.warehouseId(), dest.warehouseId(), line(v, 1)), staff).body(), "id");
        post("/" + rejected + "/submit", staff);
        assertThat(call("POST", URL + "/" + rejected + "/reject", "{\"reason\":\"\"}", manager).statusCode()).isEqualTo(400);
        HttpResponse<String> reject = call("POST", URL + "/" + rejected + "/reject", "{\"reason\":\"Chưa cần\"}", manager);
        assertThat(field(reject.body(), "status")).isEqualTo("REJECTED");

        long tooMany = number(call("POST", URL, body(source.warehouseId(), dest.warehouseId(), line(v, 10)), staff).body(), "id");
        post("/" + tooMany + "/submit", staff);
        post("/" + tooMany + "/approve", manager);
        HttpResponse<String> failed = post("/" + tooMany + "/ship", staff);
        assertThat(failed.statusCode()).isEqualTo(409);
        assertThat(failed.body()).contains("INSUFFICIENT_STOCK");
        assertThat(stock(v, source.warehouseId())).isEqualTo(4);                             // không trừ dở dang
        assertThat(field(call("GET", URL + "/" + tooMany, null, manager).body(), "status")).isEqualTo("APPROVED");

        long ok = number(call("POST", URL, body(source.warehouseId(), dest.warehouseId(), line(v, 2)), staff).body(), "id");
        post("/" + ok + "/submit", staff);
        post("/" + ok + "/approve", manager);
        assertThat(post("/" + ok + "/ship", staff).statusCode()).isEqualTo(200);
        assertThat(post("/" + ok + "/cancel", staff).statusCode()).isEqualTo(409);           // đã xuất khỏi kho nguồn
    }
}
