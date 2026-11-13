package com.tung.warehouse.rma;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test đổi trả (UC7.1, UC7.2) và truy vết một serial qua toàn bộ vòng đời: nhập, chuyển kho, bán, nhận hàng trả. */
class RmaApiTest extends AbstractApiTest {

    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        fixtures.cleanUpAll();
        jdbcTemplate.update("delete from customers where code like 'TSTOUT%'");
    }

    private long createCustomer(String token) throws Exception {
        String body = "{\"code\":\"TSTOUT" + InventoryFixtures.random() + "\",\"name\":\"Khách thử\"}";
        return number(call("POST", "/api/customers", body, token).body(), "id");
    }

    private void receiveSerial(TestWarehouse w, TestVariant v, String serial) {
        inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), "LOT-" + serial, null, null,
                new BigDecimal("1000"), 12, 1, List.of(serial), "TEST", 1L, "Nhập thử"));
    }

    private long firstLineId(String body) {
        Matcher m = Pattern.compile("\"lines\":\\[\\{\"id\":(\\d+)").matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    /** Bán một serial: tạo phiếu xuất bán, nộp, xác nhận. Trả về id phiếu xuất. */
    private long sell(TestWarehouse w, TestVariant v, long customerId, String creator, String confirmer) throws Exception {
        String body = "{\"warehouseId\":" + w.warehouseId() + ",\"purpose\":\"SALE\",\"customerId\":" + customerId
                + ",\"lines\":[{\"variantId\":" + v.variantId() + ",\"quantity\":1,\"unitPrice\":1500}]}";
        long id = number(call("POST", "/api/outbound-orders", body, creator).body(), "id");
        assertThat(call("POST", "/api/outbound-orders/" + id + "/submit", null, creator).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/outbound-orders/" + id + "/confirm", null, confirmer).statusCode()).isEqualTo(200);
        return id;
    }

    private HttpResponse<String> post(String url, String body, String token) throws Exception {
        return call("POST", url, body, token);
    }

    @Test
    @DisplayName("Vòng đời một serial: nhập, chuyển kho, bán, khách trả hàng tốt nhập lại kho; lịch sử đủ 5 sự kiện theo thứ tự")
    void serialLifecycle() throws Exception {
        TestWarehouse w1 = fixtures.createWarehouse();
        TestWarehouse w2 = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.SERIAL);
        String serial = "SN" + InventoryFixtures.random();
        receiveSerial(w1, v, serial);                                         // 1. nhập kho
        String admin = loginAndGetAccessToken("admin");
        long customerId = createCustomer(admin);

        // 2. chuyển từ kho 1 sang kho 2
        String transferBody = "{\"sourceWarehouseId\":" + w1.warehouseId() + ",\"destWarehouseId\":" + w2.warehouseId()
                + ",\"lines\":[{\"variantId\":" + v.variantId() + ",\"quantity\":1}]}";
        long transferId = number(post("/api/transfer-orders", transferBody, admin).body(), "id");
        String t = "/api/transfer-orders/" + transferId;
        post(t + "/submit", null, admin);
        post(t + "/approve", null, admin);
        HttpResponse<String> shipped = post(t + "/ship", null, admin);
        String receiveBody = "{\"lines\":[{\"lineId\":" + firstLineId(shipped.body()) + ",\"receivedQuantity\":1,\"destLocationId\":"
                + w2.locationId() + ",\"serials\":[\"" + serial + "\"]}]}";
        assertThat(post(t + "/receive", receiveBody, admin).statusCode()).isEqualTo(200);

        // 3. bán từ kho 2
        long outboundId = sell(w2, v, customerId, admin, admin);

        // 4. khách trả hàng tốt
        HttpResponse<String> rma = post("/api/rma-orders", "{\"outboundOrderId\":" + outboundId + ",\"reason\":\"Đổi ý\",\"serials\":[\"" + serial + "\"]}", admin);
        assertThat(rma.statusCode()).isEqualTo(201);
        long rmaId = number(rma.body(), "id");
        post("/api/rma-orders/" + rmaId + "/receive", null, admin);
        String processBody = "{\"lines\":[{\"lineId\":" + firstLineId(rma.body()) + ",\"condition\":\"GOOD\",\"resolution\":\"RESTOCKED\",\"locationId\":" + w2.locationId() + "}]}";
        HttpResponse<String> closed = post("/api/rma-orders/" + rmaId + "/process", processBody, admin);
        assertThat(field(closed.body(), "status")).isEqualTo("CLOSED");

        assertThat(jdbcTemplate.queryForObject("select status from device_units where serial_number = ?", String.class, serial)).isEqualTo("IN_STOCK");
        assertThat(jdbcTemplate.queryForObject("select location_id from device_units where serial_number = ?", Long.class, serial)).isEqualTo(w2.locationId());

        // Lịch sử của serial: nhập, chuyển đi, chuyển đến, bán, nhận hàng trả
        String history = call("GET", "/api/inventory/serials/history?serial=" + serial, null, admin).body();
        int a = history.indexOf("\"INBOUND\"");
        int b = history.indexOf("\"TRANSFER_OUT\"");
        int c = history.indexOf("\"TRANSFER_IN\"");
        int d = history.indexOf("\"OUTBOUND\"");
        int e = history.indexOf("\"RMA_IN\"");
        assertThat(a).isGreaterThanOrEqualTo(0);
        assertThat(a).isLessThan(b);
        assertThat(b).isLessThan(c);
        assertThat(c).isLessThan(d);
        assertThat(d).isLessThan(e);
    }

    @Test
    @DisplayName("Quy tắc: serial lạ bị từ chối; hàng lỗi vào vị trí hàng lỗi; hết bảo hành thì Nhân viên kho bị 403, Quản lý kho xử lý được")
    void rules() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.SERIAL);
        String serial = "SN" + InventoryFixtures.random();
        receiveSerial(w, v, serial);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long outboundId = sell(w, v, createCustomer(manager), staff, manager);

        assertThat(post("/api/rma-orders", "{\"outboundOrderId\":" + outboundId + ",\"reason\":\"Lỗi\",\"serials\":[\"KHONG-CO\"]}", staff).statusCode()).isEqualTo(409);

        HttpResponse<String> rma = post("/api/rma-orders", "{\"outboundOrderId\":" + outboundId + ",\"reason\":\"Máy lỗi\",\"serials\":[\"" + serial + "\"]}", staff);
        assertThat(rma.statusCode()).isEqualTo(201);
        long rmaId = number(rma.body(), "id");
        long lineId = firstLineId(rma.body());
        String process = "/api/rma-orders/" + rmaId + "/process";
        assertThat(post(process, "{\"lines\":[{\"lineId\":" + lineId + ",\"condition\":\"GOOD\",\"resolution\":\"RESTOCKED\"}]}", staff).statusCode()).isEqualTo(409); // chưa RECEIVED
        post("/api/rma-orders/" + rmaId + "/receive", null, staff);

        String defective = "{\"lines\":[{\"lineId\":" + lineId + ",\"condition\":\"DEFECTIVE\",\"resolution\":\"RESTOCKED\"}]}";
        assertThat(post(process, defective, staff).statusCode()).isEqualTo(400);              // hàng lỗi không được nhập lại kho

        // Làm hết hạn bảo hành: Nhân viên kho không xử lý được, Quản lý kho xử lý được
        jdbcTemplate.update("update device_units set warranty_end_date = '2020-01-01' where serial_number = ?", serial);
        String scrapped = "{\"lines\":[{\"lineId\":" + lineId + ",\"condition\":\"DEFECTIVE\",\"resolution\":\"SCRAPPED\"}]}";
        HttpResponse<String> forbidden = post(process, scrapped, staff);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("MANAGER_APPROVAL_REQUIRED");

        assertThat(post(process, scrapped, manager).statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select status from device_units where serial_number = ?", String.class, serial)).isEqualTo("DEFECTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                select l.location_type from device_units d join storage_locations l on l.id = d.location_id where d.serial_number = ?
                """, String.class, serial)).isEqualTo("DEFECTIVE");
    }
}
