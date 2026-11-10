package com.tung.warehouse.outbound;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test xuất kho (UC4.1 đến UC4.5): FIFO, giữ chỗ, xác nhận, hủy, phân quyền và tranh chấp đồng thời. */
class OutboundApiTest extends AbstractApiTest {

    private static final String URL = "/api/outbound-orders";
    private static final int ORDERS = 30;   // số phiếu tranh nhau trong test đồng thời; có thể tăng lên 50 hoặc 100

    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        String variants = "(select id from product_variants where sku like 'TSTINV%')";
        String lines = "(select id from outbound_order_lines where variant_id in " + variants + ")";
        List<Long> orderIds = jdbcTemplate.queryForList(
                "select distinct outbound_order_id from outbound_order_lines where variant_id in " + variants, Long.class);
        jdbcTemplate.update("delete from outbound_allocations where outbound_line_id in " + lines);
        jdbcTemplate.update("delete from inventory_reservations where outbound_line_id in " + lines);
        jdbcTemplate.update("delete from outbound_order_lines where variant_id in " + variants);
        if (!orderIds.isEmpty()) {
            String ids = orderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.update("delete from audit_logs where entity_type = 'OUTBOUND_ORDER' and entity_id in (" + ids + ")");
            jdbcTemplate.update("delete from outbound_orders where id in (" + ids + ")");
        }
        jdbcTemplate.update("delete from customers where code like 'TSTOUT%'");
        fixtures.cleanUp();
    }

    // ---------- Hàm hỗ trợ ----------

    private void receive(TestWarehouse w, TestVariant v, String batch, int qty, String cost, List<String> serials) {
        inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), batch, null, null,
                new BigDecimal(cost), 12, qty, serials, "TEST", 1L, "Nhập thử"));
    }

    private long createCustomer(String manager) throws Exception {
        String body = "{\"code\":\"TSTOUT" + InventoryFixtures.random() + "\",\"name\":\"Khách thử\"}";
        return number(call("POST", "/api/customers", body, manager).body(), "id");
    }

    private static String line(TestVariant v, int quantity, String price) {
        return "{\"variantId\":" + v.variantId() + ",\"quantity\":" + quantity + ",\"unitPrice\":" + price + "}";
    }

    private static String order(long warehouseId, String purpose, Long customerId, String... lines) {
        return "{\"warehouseId\":" + warehouseId + ",\"purpose\":\"" + purpose + "\",\"customerId\":" + customerId
                + ",\"note\":\"Xuất thử\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    private HttpResponse<String> post(String path, String token) throws Exception {
        return call("POST", URL + path, null, token);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private int sum(String column, TestVariant v) {
        return count("select coalesce(sum(" + column + "), 0) from inventory_balances where variant_id = ?", v.variantId());
    }

    private int lot(String column, TestVariant v, String batchCode) {
        return count("select " + column + " from inventory_balances b join inventory_batches ib on ib.id = b.batch_id "
                + "where b.variant_id = ? and ib.batch_code = ?", v.variantId(), batchCode);
    }

    // ---------- Các test ----------

    @Test
    @DisplayName("Luồng đầy đủ: giữ chỗ theo FIFO từ nhiều lô, xác nhận xuất, phân bổ, serial SOLD, bảo hành, lợi nhuận")
    void fullFlowWithFifo() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant quantity = fixtures.createVariant(TrackingType.QUANTITY);
        TestVariant serial = fixtures.createVariant(TrackingType.SERIAL);
        String r = InventoryFixtures.random();
        receive(w, quantity, "LOT1", 5, "100", null);            // lô cũ hơn
        receive(w, quantity, "LOT2", 5, "120", null);
        receive(w, serial, "LOTS", 2, "1000", List.of("S1" + r, "S2" + r));
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long customerId = createCustomer(manager);

        HttpResponse<String> created = call("POST", URL, order(w.warehouseId(), "SALE", customerId,
                line(quantity, 7, "200"), line(serial, 2, "1500")), staff);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(field(created.body(), "status")).isEqualTo("DRAFT");
        assertThat(field(created.body(), "code")).startsWith("XK-");
        long id = number(created.body(), "id");
        assertThat(sum("reserved", quantity)).isZero();           // tạo phiếu chưa giữ chỗ

        // Nộp phiếu: giữ chỗ theo FIFO, lô cũ lấy hết trước rồi mới sang lô sau
        assertThat(field(post("/" + id + "/submit", staff).body(), "status")).isEqualTo("RESERVED");
        assertThat(lot("reserved", quantity, "LOT1")).isEqualTo(5);
        assertThat(lot("reserved", quantity, "LOT2")).isEqualTo(2);
        assertThat(count("select count(*) from device_units where status = 'RESERVED' and variant_id = ?", serial.variantId())).isEqualTo(2);
        assertThat(sum("on_hand", quantity)).isEqualTo(10);       // giữ chỗ chưa làm giảm tồn thực tế

        // Xác nhận xuất kho
        HttpResponse<String> confirmed = post("/" + id + "/confirm", manager);
        assertThat(confirmed.statusCode()).isEqualTo(200);
        assertThat(field(confirmed.body(), "status")).isEqualTo("COMPLETED");
        assertThat(confirmed.body()).contains("1660");           // doanh thu 4400 trừ giá vốn (500 + 240 + 2000)

        assertThat(lot("on_hand", quantity, "LOT1")).isZero();
        assertThat(lot("on_hand", quantity, "LOT2")).isEqualTo(3);
        assertThat(sum("reserved", quantity)).isZero();
        assertThat(sum("on_hand", serial)).isZero();
        assertThat(count("select count(*) from device_units where status = 'SOLD' and warranty_end_date is not null and variant_id = ?", serial.variantId())).isEqualTo(2);
        assertThat(count("select count(*) from outbound_allocations a join outbound_order_lines l on l.id = a.outbound_line_id where l.outbound_order_id = ?", id)).isEqualTo(4);
        assertThat(count("select count(*) from inventory_transactions where type = 'OUTBOUND' and reference_id = ?", id)).isEqualTo(3);

        // Xác nhận hoặc hủy lần nữa đều bị từ chối
        assertThat(post("/" + id + "/confirm", manager).statusCode()).isEqualTo(409);
        assertThat(post("/" + id + "/cancel", staff).statusCode()).isEqualTo(409);
        assertThat(call("GET", URL + "/" + id, null, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Thiếu hàng thì nộp phiếu bị 409 và không giữ chỗ dở dang; hủy phiếu trả giữ chỗ cho phiếu khác dùng")
    void insufficientStockAndCancel() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        receive(w, v, "LOT1", 10, "100", null);
        String staff = loginAndGetAccessToken("staff");

        long tooMany = number(call("POST", URL, order(w.warehouseId(), "INTERNAL", null, line(v, 20, "1")), staff).body(), "id");
        HttpResponse<String> failed = post("/" + tooMany + "/submit", staff);
        assertThat(failed.statusCode()).isEqualTo(409);
        assertThat(failed.body()).contains("INSUFFICIENT_STOCK");
        assertThat(sum("reserved", v)).isZero();

        long a = number(call("POST", URL, order(w.warehouseId(), "INTERNAL", null, line(v, 6, "1")), staff).body(), "id");
        long b = number(call("POST", URL, order(w.warehouseId(), "INTERNAL", null, line(v, 6, "1")), staff).body(), "id");
        assertThat(post("/" + a + "/submit", staff).statusCode()).isEqualTo(200);
        assertThat(sum("reserved", v)).isEqualTo(6);
        assertThat(post("/" + b + "/submit", staff).statusCode()).isEqualTo(409);   // chỉ còn 4 khả dụng
        assertThat(sum("reserved", v)).isEqualTo(6);

        assertThat(field(post("/" + a + "/cancel", staff).body(), "status")).isEqualTo("CANCELLED");
        assertThat(sum("reserved", v)).isZero();
        assertThat(post("/" + b + "/submit", staff).statusCode()).isEqualTo(200);   // giờ đủ hàng
    }

    @Test
    @DisplayName("Phân quyền và quy tắc: Kinh doanh tạo được nhưng không xác nhận được; xuất bán phải có khách; không trùng biến thể")
    void permissionsAndRules() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestWarehouse other = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        receive(w, v, "LOT1", 10, "100", null);
        String sales = loginAndGetAccessToken("sales");
        String staff = loginAndGetAccessToken("staff");
        String body = order(w.warehouseId(), "INTERNAL", null, line(v, 2, "5"));

        assertThat(call("POST", URL, body, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(403);
        assertThat(call("POST", URL, order(other.warehouseId(), "INTERNAL", null, line(v, 1, "5")), staff).statusCode()).isEqualTo(403);
        assertThat(call("POST", URL, order(w.warehouseId(), "SALE", null, line(v, 1, "5")), staff).statusCode()).isEqualTo(400); // thiếu khách hàng
        assertThat(call("POST", URL, order(w.warehouseId(), "INTERNAL", null, line(v, 1, "5"), line(v, 2, "5")), staff).statusCode()).isEqualTo(409);

        long id = number(call("POST", URL, body, sales).body(), "id");                      // Kinh doanh tạo và nộp được
        assertThat(post("/" + id + "/confirm", staff).statusCode()).isEqualTo(409);         // chưa nộp: phiếu còn DRAFT
        assertThat(post("/" + id + "/submit", sales).statusCode()).isEqualTo(200);
        assertThat(post("/" + id + "/confirm", sales).statusCode()).isEqualTo(403);         // Kinh doanh không được xác nhận
        assertThat(post("/" + id + "/confirm", staff).statusCode()).isEqualTo(403);         // Nhân viên kho cũng không
        assertThat(call("PUT", URL + "/" + id, body, sales).statusCode()).isEqualTo(409);   // đã giữ chỗ thì không sửa được
    }

    /** Gửi cùng lúc nhiều yêu cầu POST, mỗi yêu cầu một luồng, và trả về mã trạng thái của từng yêu cầu. */
    private List<Integer> postConcurrently(List<String> paths, String token) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(paths.size());
        List<Future<Integer>> futures = new ArrayList<>();
        for (String path : paths) {
            Callable<Integer> task = () -> post(path, token).statusCode();
            futures.add(pool.submit(task));
        }
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> future : futures) {
            codes.add(future.get(180, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return codes;
    }

    /** 30 phiếu, mỗi phiếu xuất 1 sản phẩm, cùng tranh một lô chỉ có 10: đúng 10 phiếu giữ chỗ được. */
    private void raceForLastItems(TrackingType type) throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant v = fixtures.createVariant(type);
        String r = InventoryFixtures.random();
        List<String> serials = type == TrackingType.SERIAL
                ? java.util.stream.IntStream.range(0, 10).mapToObj(i -> "SN" + r + i).toList() : null;
        receive(w, v, "LOT1", 10, "100", serials);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            ids.add(number(call("POST", URL, order(w.warehouseId(), "INTERNAL", null, line(v, 1, "1")), staff).body(), "id"));
        }

        // Cùng lúc nộp 30 phiếu: đúng 10 thành công, 20 bị từ chối vì hết hàng
        List<Integer> submitCodes = postConcurrently(ids.stream().map(id -> "/" + id + "/submit").toList(), staff);
        assertThat(submitCodes.stream().filter(c -> c == 200).count()).isEqualTo(10);
        assertThat(submitCodes.stream().filter(c -> c == 409).count()).isEqualTo(ORDERS - 10);
        assertThat(sum("reserved", v)).isEqualTo(10);
        assertThat(sum("on_hand", v)).isEqualTo(10);

        // Cùng lúc xác nhận 10 phiếu đã giữ chỗ: tất cả thành công, tồn về 0 và không bao giờ âm
        List<String> confirmPaths = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            if (submitCodes.get(i) == 200) confirmPaths.add("/" + ids.get(i) + "/confirm");
        }
        assertThat(postConcurrently(confirmPaths, manager)).allMatch(code -> code == 200);
        assertThat(sum("on_hand", v)).isZero();
        assertThat(sum("reserved", v)).isZero();
        assertThat(count("select count(*) from inventory_transactions where type = 'OUTBOUND' and variant_id = ?", v.variantId())).isEqualTo(10);

        if (type == TrackingType.SERIAL) {   // không phân bổ trùng serial
            String from = "from outbound_allocations a join outbound_order_lines l on l.id = a.outbound_line_id where l.variant_id = ?";
            assertThat(count("select count(*) " + from, v.variantId())).isEqualTo(10);
            assertThat(count("select count(distinct a.serial_number) " + from, v.variantId())).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("Đồng thời, hàng theo số lượng: 30 phiếu tranh 10 sản phẩm, đúng 10 thành công, tồn không âm")
    void concurrentOrdersOnQuantityItems() throws Exception {
        raceForLastItems(TrackingType.QUANTITY);
    }

    @Test
    @DisplayName("Đồng thời, hàng theo serial: 30 phiếu tranh 10 thiết bị, đúng 10 thành công, không phân bổ trùng serial")
    void concurrentOrdersOnSerialItems() throws Exception {
        raceForLastItems(TrackingType.SERIAL);
    }
}
