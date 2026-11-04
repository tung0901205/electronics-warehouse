package com.tung.warehouse.inventory;

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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test các API tra cứu tồn kho (UC8.1, UC8.2, UC8.3) qua HTTP thật.
 * Dữ liệu tồn được dựng bằng cách gọi thẳng InventoryService (vì tồn chưa có API ghi trực tiếp).
 */
class InventoryApiTest extends AbstractApiTest {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryFixtures fixtures;

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private ReceiveResult receive(TestWarehouse w, TestVariant v, String batchCode, int quantity, List<String> serials) {
        return inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), batchCode,
                null, null, new BigDecimal("250.00"), 12, quantity, serials, "TEST", 1L, "Nhập thử"));
    }

    private void out(long balanceId, int quantity, List<String> serials) {
        inventoryService.decrease(new StockOutCommand(balanceId, quantity, TransactionType.OUTBOUND, false, serials,
                "TEST", 2L, "Xuất thử"));
    }

    @Test
    @DisplayName("Tài khoản được gán kho và Admin đều thấy tồn của kho mẫu; lô nhập trước nằm trên (thứ tự FIFO)")
    void assignedUsersSeeTheirWarehouseStock() throws Exception {
        TestWarehouse seeded = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        receive(seeded, variant, "LOT-1", 10, null);
        receive(seeded, variant, "LOT-2", 5, null);
        String url = "/api/inventory/balances?keyword=" + variant.sku().toLowerCase();

        for (String username : List.of("manager", "viewer", "admin")) {
            HttpResponse<String> response = call("GET", url, null, loginAndGetAccessToken(username));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(number(response.body(), "totalElements")).isEqualTo(2);
            assertThat(response.body()).contains("\"onHand\":10").contains("\"available\":10").contains(variant.sku());
            assertThat(response.body().indexOf("LOT-1")).isLessThan(response.body().indexOf("LOT-2"));
        }
    }

    @Test
    @DisplayName("Kho không được gán thì không thấy tồn (danh sách trống) và chọn thẳng kho đó bị 403; Admin thấy")
    void scopeHidesOtherWarehouses() throws Exception {
        TestWarehouse other = fixtures.createWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        receive(other, variant, "LOT-1", 10, null);
        String manager = loginAndGetAccessToken("manager");
        String admin = loginAndGetAccessToken("admin");
        String url = "/api/inventory/balances?keyword=" + variant.sku().toLowerCase();

        assertThat(number(call("GET", url, null, manager).body(), "totalElements")).isZero();

        HttpResponse<String> denied = call("GET", url + "&warehouseId=" + other.warehouseId(), null, manager);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("WAREHOUSE_ACCESS_DENIED");

        assertThat(number(call("GET", url, null, admin).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", url + "&warehouseId=" + other.warehouseId(), null, admin).body(), "totalElements")).isEqualTo(1);
    }

    @Test
    @DisplayName("Tồn cộng dồn theo biến thể: cộng các lô, hiển thị đúng số giữ chỗ và khả dụng")
    void summarySumsBatches() throws Exception {
        TestWarehouse seeded = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult first = receive(seeded, variant, "LOT-1", 10, null);
        receive(seeded, variant, "LOT-2", 5, null);
        inventoryService.reserve(first.balanceId(), 4);
        String manager = loginAndGetAccessToken("manager");

        HttpResponse<String> response = call("GET", "/api/inventory/summary?keyword=" + variant.sku().toLowerCase(), null, manager);

        assertThat(number(response.body(), "totalElements")).isEqualTo(1);
        assertThat(response.body()).contains("\"onHand\":15").contains("\"reserved\":4").contains("\"available\":11");
    }

    @Test
    @DisplayName("Dòng đã hết hàng mặc định bị ẩn, chỉ hiện khi includeZero=true")
    void includeZeroShowsEmptyRows() throws Exception {
        TestWarehouse seeded = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(seeded, variant, "LOT-1", 3, null);
        out(result.balanceId(), 3, null);
        String manager = loginAndGetAccessToken("manager");
        String url = "/api/inventory/balances?keyword=" + variant.sku().toLowerCase();

        assertThat(number(call("GET", url, null, manager).body(), "totalElements")).isZero();
        assertThat(number(call("GET", url + "&includeZero=true", null, manager).body(), "totalElements")).isEqualTo(1);
    }

    @Test
    @DisplayName("Lịch sử giao dịch: mới nhất lên đầu, lọc theo loại và theo phiếu gốc")
    void transactionHistoryAndFilters() throws Exception {
        TestWarehouse seeded = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(seeded, variant, "LOT-1", 10, null);   // phiếu gốc TEST/1
        out(result.balanceId(), 3, null);                                      // phiếu gốc TEST/2
        String viewer = loginAndGetAccessToken("viewer");
        String base = "/api/inventory/transactions?variantId=" + variant.variantId();

        HttpResponse<String> all = call("GET", base, null, viewer);
        assertThat(number(all.body(), "totalElements")).isEqualTo(2);
        assertThat(all.body().indexOf("OUTBOUND")).isLessThan(all.body().indexOf("INBOUND")); // mới nhất lên đầu

        HttpResponse<String> outbound = call("GET", base + "&type=OUTBOUND", null, viewer);
        assertThat(number(outbound.body(), "totalElements")).isEqualTo(1);
        assertThat(outbound.body()).contains("\"quantityDelta\":-3");

        assertThat(number(call("GET", base + "&type=INBOUND", null, viewer).body(), "totalElements")).isEqualTo(1);
        assertThat(number(call("GET", base + "&referenceType=TEST&referenceId=2", null, viewer).body(), "totalElements")).isEqualTo(1);

        assertThat(call("GET", base, null, null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Tra cứu theo serial: thấy trạng thái hiện tại, xuất bán xong thì thành SOLD")
    void serialLookup() throws Exception {
        TestWarehouse seeded = fixtures.seededWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.SERIAL);
        String serial = "SN" + InventoryFixtures.random();
        ReceiveResult result = receive(seeded, variant, "LOT-S", 1, List.of(serial));
        String manager = loginAndGetAccessToken("manager");

        HttpResponse<String> inStock = call("GET", "/api/inventory/serials?serial=" + serial, null, manager);
        assertThat(inStock.statusCode()).isEqualTo(200);
        assertThat(inStock.body()).contains("\"status\":\"IN_STOCK\"").contains(variant.sku()).contains("KHO-HN");

        out(result.balanceId(), 1, List.of(serial));
        HttpResponse<String> sold = call("GET", "/api/inventory/serials?serial=" + serial, null, manager);
        assertThat(sold.body()).contains("\"status\":\"SOLD\"");

        assertThat(call("GET", "/api/inventory/serials?serial=KHONG-CO-" + serial, null, manager).body()).isEqualTo("[]");
        assertThat(call("GET", "/api/inventory/serials?serial=", null, manager).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Serial đang nằm ở kho không được gán thì ẩn với người thường, Admin vẫn thấy")
    void serialInOtherWarehouseIsHidden() throws Exception {
        TestWarehouse other = fixtures.createWarehouse();
        TestVariant variant = fixtures.createVariant(TrackingType.SERIAL);
        String serial = "SN" + InventoryFixtures.random();
        receive(other, variant, "LOT-S", 1, List.of(serial));

        String url = "/api/inventory/serials?serial=" + serial;
        assertThat(call("GET", url, null, loginAndGetAccessToken("manager")).body()).isEqualTo("[]");
        assertThat(call("GET", url, null, loginAndGetAccessToken("admin")).body()).contains(serial);
    }

    @Test
    @DisplayName("Chưa đăng nhập thì không xem được tồn kho (401)")
    void requiresLogin() throws Exception {
        assertThat(call("GET", "/api/inventory/balances", null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/inventory/summary", null, null).statusCode()).isEqualTo(401);
    }
}
