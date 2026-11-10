package com.tung.warehouse.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.tung.warehouse.catalog.entity.TrackingType;
import com.tung.warehouse.support.AbstractApiTest;
import com.tung.warehouse.support.InventoryFixtures;
import com.tung.warehouse.support.InventoryFixtures.TestVariant;
import com.tung.warehouse.support.InventoryFixtures.TestWarehouse;
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

/**
 * Test luồng nhập kho (UC3.1 đến UC3.5) qua HTTP thật, kiểm tra dữ liệu thật trong database sau mỗi bước.
 * Dùng biến thể, kho từ InventoryFixtures (tiền tố TSTINV) và nhà cung cấp có mã TSTINB.
 */
class InboundApiTest extends AbstractApiTest {

    private static final String URL = "/api/inbound-orders";

    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Dọn theo thứ tự khóa ngoại: nhật ký, biên bản, gỡ liên kết lô, serial, dòng, phiếu, nhà cung cấp, rồi dữ liệu tồn. */
    @AfterEach
    void cleanUp() {
        String variants = "(select id from product_variants where sku like 'TSTINV%')";
        List<Long> orderIds = jdbcTemplate.queryForList(
                "select distinct inbound_order_id from inbound_order_lines where variant_id in " + variants, Long.class);
        String ids = orderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (!orderIds.isEmpty()) {
            jdbcTemplate.update("delete from audit_logs where entity_type = 'INBOUND_ORDER' and entity_id in (" + ids + ")");
            jdbcTemplate.update("delete from inbound_receipts where inbound_order_id in (" + ids + ")");
        }
        jdbcTemplate.update("update inventory_batches set inbound_line_id = null where variant_id in " + variants);
        jdbcTemplate.update("delete from inbound_line_serials where inbound_line_id in "
                + "(select id from inbound_order_lines where variant_id in " + variants + ")");
        jdbcTemplate.update("delete from inbound_order_lines where variant_id in " + variants);
        if (!orderIds.isEmpty()) {
            jdbcTemplate.update("delete from inbound_orders where id in (" + ids + ")");
        }
        jdbcTemplate.update("delete from suppliers where code like 'TSTINB%'");
        fixtures.cleanUp();
    }

    // ---------- Các hàm dựng dữ liệu ----------

    private long createSupplier(String manager) throws Exception {
        String body = "{\"code\":\"TSTINB" + InventoryFixtures.random() + "\",\"name\":\"Nhà cung cấp thử\"}";
        HttpResponse<String> response = call("POST", "/api/suppliers", body, manager);
        assertThat(response.statusCode()).isEqualTo(201);
        return number(response.body(), "id");
    }

    private static String serials(String... values) {
        return "[" + java.util.Arrays.stream(values).map(v -> "\"" + v + "\"").collect(Collectors.joining(",")) + "]";
    }

    private static String line(TestVariant variant, long locationId, int quantity, String cost, String batchCode, String serialsJson) {
        return """
                {"variantId":%d,"locationId":%d,"quantity":%d,"unitCost":%s,"batchCode":"%s","serials":%s}
                """.formatted(variant.variantId(), locationId, quantity, cost, batchCode, serialsJson);
    }

    private static String order(long warehouseId, long supplierId, String... lines) {
        return """
                {"warehouseId":%d,"supplierId":%d,"expectedDate":"2026-12-01","note":"Nhập thử","lines":[%s]}
                """.formatted(warehouseId, supplierId, String.join(",", lines));
    }

    private int stock(TestVariant... variants) {
        String ids = java.util.Arrays.stream(variants).map(v -> String.valueOf(v.variantId())).collect(Collectors.joining(","));
        return jdbcTemplate.queryForObject("select coalesce(sum(on_hand), 0) from inventory_balances where variant_id in (" + ids + ")", Integer.class);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    // ---------- Các test ----------

    @Test
    @DisplayName("Luồng đầy đủ: tạo, nộp chưa tăng tồn; xác nhận mới tăng tồn, tạo lô, serial, sổ, biên bản, nhật ký")
    void fullFlow() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant serial1 = fixtures.createVariant(TrackingType.SERIAL);
        TestVariant serial2 = fixtures.createVariant(TrackingType.SERIAL);
        TestVariant quantity = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long supplierId = createSupplier(manager);
        String r = InventoryFixtures.random();

        String body = order(w.warehouseId(), supplierId,
                line(serial1, w.locationId(), 2, "22500000", "L1-" + r, serials("A1" + r, "A2" + r)),
                line(serial2, w.locationId(), 1, "28000000", "L2-" + r, serials("B1" + r)),
                line(quantity, w.locationId(), 50, "320000", "L3-" + r, "null"));

        // Tạo phiếu nháp
        HttpResponse<String> created = call("POST", URL, body, staff);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(field(created.body(), "status")).isEqualTo("DRAFT");
        assertThat(field(created.body(), "code")).startsWith("NK-");
        assertThat(created.body()).contains("\"totalQuantity\":53").contains("89000000");
        long id = number(created.body(), "id");
        assertThat(stock(serial1, serial2, quantity)).isZero();     // tạo phiếu KHÔNG tăng tồn

        // Nộp phiếu
        HttpResponse<String> submitted = call("POST", URL + "/" + id + "/submit", null, staff);
        assertThat(field(submitted.body(), "status")).isEqualTo("SUBMITTED");
        assertThat(stock(serial1, serial2, quantity)).isZero();     // nộp phiếu cũng KHÔNG tăng tồn

        // Xác nhận
        HttpResponse<String> confirmed = call("POST", URL + "/" + id + "/confirm", null, manager);
        assertThat(confirmed.statusCode()).isEqualTo(200);
        assertThat(field(confirmed.body(), "status")).isEqualTo("CONFIRMED");

        assertThat(stock(serial1, serial2, quantity)).isEqualTo(53);
        assertThat(count("select count(*) from inventory_transactions where reference_type = 'INBOUND_ORDER' and reference_id = ?", id)).isEqualTo(3);
        assertThat(count("select count(*) from device_units where status = 'IN_STOCK' and variant_id in (?, ?)",
                serial1.variantId(), serial2.variantId())).isEqualTo(3);
        assertThat(count("select count(*) from inventory_batches where inbound_line_id is not null and variant_id in (?, ?, ?)",
                serial1.variantId(), serial2.variantId(), quantity.variantId())).isEqualTo(3);
        assertThat(count("select count(*) from inbound_receipts where inbound_order_id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where entity_type = 'INBOUND_ORDER' and entity_id = ?", id)).isEqualTo(3);

        // Xác nhận lần hai và mọi thao tác khác trên phiếu đã xác nhận đều bị từ chối
        assertThat(call("POST", URL + "/" + id + "/confirm", null, manager).statusCode()).isEqualTo(409);
        assertThat(call("POST", URL + "/" + id + "/cancel", null, staff).statusCode()).isEqualTo(409);
        assertThat(call("POST", URL + "/" + id + "/submit", null, staff).statusCode()).isEqualTo(409);
        assertThat(call("PUT", URL + "/" + id, body, staff).statusCode()).isEqualTo(409);
        assertThat(stock(serial1, serial2, quantity)).isEqualTo(53);   // tồn không tăng thêm

        // Người xem đọc được phiếu
        HttpResponse<String> viewed = call("GET", URL + "/" + id, null, loginAndGetAccessToken("viewer"));
        assertThat(viewed.statusCode()).isEqualTo(200);
        assertThat(viewed.body()).contains("CONFIRMED").contains(serial1.sku());
    }

    @Test
    @DisplayName("Phân quyền và phạm vi kho: Kinh doanh, Người xem không tạo được; Nhân viên không xác nhận được; kho lạ bị 403")
    void permissionsAndScope() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestWarehouse otherWarehouse = fixtures.createWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        String admin = loginAndGetAccessToken("admin");
        long supplierId = createSupplier(manager);
        String r = InventoryFixtures.random();
        String body = order(w.warehouseId(), supplierId, line(variant, w.locationId(), 5, "100", "L-" + r, "null"));

        assertThat(call("POST", URL, body, loginAndGetAccessToken("sales")).statusCode()).isEqualTo(403);
        assertThat(call("POST", URL, body, loginAndGetAccessToken("viewer")).statusCode()).isEqualTo(403);
        assertThat(call("POST", URL, body, null).statusCode()).isEqualTo(401);

        long id = number(call("POST", URL, body, staff).body(), "id");
        call("POST", URL + "/" + id + "/submit", null, staff);
        assertThat(call("POST", URL + "/" + id + "/confirm", null, staff).statusCode()).isEqualTo(403); // chỉ Quản lý kho trở lên

        // Kho mà staff không được gán
        String otherBody = order(otherWarehouse.warehouseId(), supplierId,
                line(variant, otherWarehouse.locationId(), 5, "100", "LO-" + r, "null"));
        HttpResponse<String> denied = call("POST", URL, otherBody, staff);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("WAREHOUSE_ACCESS_DENIED");

        HttpResponse<String> adminCreated = call("POST", URL, otherBody, admin);
        assertThat(adminCreated.statusCode()).isEqualTo(201);
        long otherId = number(adminCreated.body(), "id");
        String otherCode = field(adminCreated.body(), "code");

        assertThat(call("GET", URL + "/" + otherId, null, staff).statusCode()).isEqualTo(403);
        assertThat(number(call("GET", URL + "?keyword=" + otherCode, null, staff).body(), "totalElements")).isZero();
        assertThat(number(call("GET", URL + "?keyword=" + otherCode, null, admin).body(), "totalElements")).isEqualTo(1);
    }

    @Test
    @DisplayName("Dữ liệu phiếu sai bị từ chối: danh sách trống, giá âm, serial sai, trùng, vị trí kho khác, thiếu serial khi nộp")
    void validationRules() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestWarehouse other = fixtures.createWarehouse();
        TestVariant serialVariant = fixtures.createVariant(TrackingType.SERIAL);
        TestVariant quantityVariant = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        long supplierId = createSupplier(loginAndGetAccessToken("manager"));
        String r = InventoryFixtures.random();
        long loc = w.locationId();

        HttpResponse<String> empty = call("POST", URL, order(w.warehouseId(), supplierId), staff);
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(empty.body()).contains("VALIDATION_ERROR").contains("lines");

        assertThat(call("POST", URL, order(w.warehouseId(), supplierId,
                line(quantityVariant, loc, 1, "-5", "A" + r, "null")), staff).statusCode()).isEqualTo(400);           // giá âm
        assertThat(call("POST", URL, order(w.warehouseId(), supplierId,
                line(quantityVariant, loc, 1, "5", "B" + r, serials("X" + r))), staff).statusCode()).isEqualTo(400);   // hàng số lượng có serial
        assertThat(call("POST", URL, order(w.warehouseId(), supplierId,
                line(serialVariant, loc, 1, "5", "C" + r, serials("S1" + r, "S2" + r))), staff).statusCode()).isEqualTo(400); // serial nhiều hơn số lượng
        assertThat(call("POST", URL, order(w.warehouseId(), supplierId,
                line(serialVariant, loc, 1, "5", "D" + r, serials("DUP" + r)),
                line(serialVariant, loc, 1, "5", "E" + r, serials("DUP" + r))), staff).statusCode()).isEqualTo(409);   // trùng serial trong phiếu
        assertThat(call("POST", URL, order(w.warehouseId(), supplierId,
                line(quantityVariant, loc, 1, "5", "SAME" + r, "null"),
                line(quantityVariant, loc, 2, "5", "same" + r, "null")), staff).statusCode()).isEqualTo(409);          // trùng mã lô trong phiếu
        assertThat(call("POST", URL, order(w.warehouseId(), supplierId,
                line(quantityVariant, other.locationId(), 1, "5", "F" + r, "null")), staff).statusCode()).isEqualTo(409); // vị trí của kho khác

        String unknownVariant = """
                {"warehouseId":%d,"supplierId":%d,"lines":[{"variantId":999999999,"locationId":%d,"quantity":1,"unitCost":5,"batchCode":"G%s"}]}
                """.formatted(w.warehouseId(), supplierId, loc, r);
        assertThat(call("POST", URL, unknownVariant, staff).statusCode()).isEqualTo(404);

        // Phiếu nháp được thiếu serial, nhưng nộp phiếu thì bị từ chối
        HttpResponse<String> draft = call("POST", URL, order(w.warehouseId(), supplierId,
                line(serialVariant, loc, 2, "5", "H" + r, serials("ONLY" + r))), staff);
        assertThat(draft.statusCode()).isEqualTo(201);
        HttpResponse<String> submit = call("POST", URL + "/" + number(draft.body(), "id") + "/submit", null, staff);
        assertThat(submit.statusCode()).isEqualTo(400);
        assertThat(submit.body()).contains("SERIAL_COUNT_MISMATCH");
    }

    @Test
    @DisplayName("Xác nhận lỗi giữa chừng (serial đã tồn tại) thì hoàn tác toàn bộ: phiếu vẫn SUBMITTED, không có lô hay tồn nào")
    void confirmFailureRollsBackEverything() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant serialVariant = fixtures.createVariant(TrackingType.SERIAL);
        TestVariant quantityVariant = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long supplierId = createSupplier(manager);
        String r = InventoryFixtures.random();
        String duplicate = "DUP" + r;

        // Phiếu 1 nhập serial DUP... thành công
        long first = number(call("POST", URL, order(w.warehouseId(), supplierId,
                line(serialVariant, w.locationId(), 1, "100", "OLD-" + r, serials(duplicate))), staff).body(), "id");
        call("POST", URL + "/" + first + "/submit", null, staff);
        assertThat(call("POST", URL + "/" + first + "/confirm", null, manager).statusCode()).isEqualTo(200);

        // Phiếu 2: dòng 1 hợp lệ, dòng 2 dùng lại serial đã tồn tại
        long second = number(call("POST", URL, order(w.warehouseId(), supplierId,
                line(quantityVariant, w.locationId(), 5, "10", "NEWQ-" + r, "null"),
                line(serialVariant, w.locationId(), 1, "100", "NEWS-" + r, serials(duplicate))), staff).body(), "id");
        assertThat(call("POST", URL + "/" + second + "/submit", null, staff).statusCode()).isEqualTo(200);

        HttpResponse<String> failed = call("POST", URL + "/" + second + "/confirm", null, manager);
        assertThat(failed.statusCode()).isEqualTo(409);

        assertThat(field(call("GET", URL + "/" + second, null, manager).body(), "status")).isEqualTo("SUBMITTED");
        assertThat(count("select count(*) from inventory_batches where variant_id = ?", quantityVariant.variantId())).isZero();
        assertThat(stock(quantityVariant)).isZero();
        assertThat(count("select count(*) from inventory_transactions where reference_type = 'INBOUND_ORDER' and reference_id = ?", second)).isZero();
        assertThat(count("select count(*) from inbound_receipts where inbound_order_id = ?", second)).isZero();
    }

    @Test
    @DisplayName("Sửa phiếu nháp thay các dòng cũ; hủy phiếu rồi thì không nộp, không xác nhận, không sửa được nữa")
    void editAndCancel() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long supplierId = createSupplier(manager);
        String r = InventoryFixtures.random();

        HttpResponse<String> created = call("POST", URL, order(w.warehouseId(), supplierId,
                line(variant, w.locationId(), 5, "100", "OLD-" + r, "null")), staff);
        long id = number(created.body(), "id");

        String newBody = order(w.warehouseId(), supplierId, line(variant, w.locationId(), 7, "100", "NEW-" + r, "null"));
        HttpResponse<String> updated = call("PUT", URL + "/" + id, newBody, staff);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("NEW-" + r).doesNotContain("OLD-" + r).contains("\"totalQuantity\":7");

        assertThat(call("POST", URL + "/" + id + "/confirm", null, manager).statusCode()).isEqualTo(409); // chưa nộp, không xác nhận được

        HttpResponse<String> cancelled = call("POST", URL + "/" + id + "/cancel", null, staff);
        assertThat(field(cancelled.body(), "status")).isEqualTo("CANCELLED");
        assertThat(call("POST", URL + "/" + id + "/submit", null, staff).statusCode()).isEqualTo(409);
        assertThat(call("POST", URL + "/" + id + "/confirm", null, manager).statusCode()).isEqualTo(409);
        assertThat(call("PUT", URL + "/" + id, newBody, staff).statusCode()).isEqualTo(409);
        assertThat(stock(variant)).isZero();
    }

    @Test
    @DisplayName("5 người cùng xác nhận một phiếu: đúng một người thành công, tồn chỉ tăng một lần")
    void concurrentConfirmHappensOnce() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long supplierId = createSupplier(manager);
        long id = number(call("POST", URL, order(w.warehouseId(), supplierId,
                line(variant, w.locationId(), 10, "100", "L-" + InventoryFixtures.random(), "null")), staff).body(), "id");
        call("POST", URL + "/" + id + "/submit", null, staff);

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> call("POST", URL + "/" + id + "/confirm", null, manager).statusCode();
            results.add(pool.submit(task));
        }
        int ok = 0;
        int conflict = 0;
        for (Future<Integer> result : results) {
            int status = result.get(60, TimeUnit.SECONDS);
            if (status == 200) ok++;
            if (status == 409) conflict++;
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(threads - 1);
        assertThat(stock(variant)).isEqualTo(10);
        assertThat(count("select count(*) from inventory_transactions where reference_type = 'INBOUND_ORDER' and reference_id = ?", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Tìm kiếm phiếu nhập kết hợp các điều kiện: mã, trạng thái, kho; có phân trang")
    void searchFilters() throws Exception {
        TestWarehouse w = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        String staff = loginAndGetAccessToken("staff");
        String manager = loginAndGetAccessToken("manager");
        long supplierId = createSupplier(manager);
        String r = InventoryFixtures.random();

        HttpResponse<String> a = call("POST", URL, order(w.warehouseId(), supplierId, line(variant, w.locationId(), 1, "10", "A-" + r, "null")), staff);
        HttpResponse<String> b = call("POST", URL, order(w.warehouseId(), supplierId, line(variant, w.locationId(), 2, "10", "B-" + r, "null")), staff);
        String codeA = field(a.body(), "code");
        call("POST", URL + "/" + number(a.body(), "id") + "/submit", null, staff);

        assertThat(number(call("GET", URL + "?keyword=" + codeA, null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", URL + "?keyword=" + codeA + "&status=SUBMITTED", null, manager).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", URL + "?keyword=" + codeA + "&status=DRAFT", null, manager).body(), "totalElements")).isZero();

        HttpResponse<String> paged = call("GET", URL + "?keyword=NK-&warehouseId=" + w.warehouseId() + "&page=0&size=1", null, manager);
        assertThat(number(paged.body(), "totalElements")).isGreaterThanOrEqualTo(2);
        assertThat(number(paged.body(), "totalPages")).isGreaterThanOrEqualTo(2);
        assertThat(b.statusCode()).isEqualTo(201);
    }
}
