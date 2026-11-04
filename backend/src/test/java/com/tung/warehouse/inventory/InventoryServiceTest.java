package com.tung.warehouse.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tung.warehouse.catalog.entity.TrackingType;
import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.InsufficientStockException;
import com.tung.warehouse.inventory.dto.ReceiveCommand;
import com.tung.warehouse.inventory.dto.ReceiveResult;
import com.tung.warehouse.inventory.dto.StockInCommand;
import com.tung.warehouse.inventory.dto.StockOutCommand;
import com.tung.warehouse.inventory.entity.TransactionType;
import com.tung.warehouse.inventory.service.InventoryService;
import com.tung.warehouse.support.InventoryFixtures;
import com.tung.warehouse.support.InventoryFixtures.TestVariant;
import com.tung.warehouse.support.InventoryFixtures.TestWarehouse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test InventoryService với MySQL thật: tăng, giảm, giữ chỗ, serial, sổ giao dịch, rollback và tranh chấp đồng thời.
 * Mỗi test tự dựng dữ liệu riêng (tiền tố TSTINV) và dọn sau khi chạy.
 */
@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    // ---------- Các hàm hỗ trợ ----------

    private ReceiveResult receive(TestWarehouse w, TestVariant v, String batchCode, int quantity, List<String> serials) {
        return inventoryService.receive(new ReceiveCommand(w.warehouseId(), w.locationId(), v.variantId(), batchCode,
                null, null, new BigDecimal("100.00"), 12, quantity, serials, "TEST", 1L, "Nhập thử"));
    }

    private void out(long balanceId, int quantity, TransactionType type, boolean fromReserved, List<String> serials) {
        inventoryService.decrease(new StockOutCommand(balanceId, quantity, type, fromReserved, serials, "TEST", 2L, "Xuất thử"));
    }

    private int onHand(long balanceId) {
        return jdbcTemplate.queryForObject("select on_hand from inventory_balances where id = ?", Integer.class, balanceId);
    }

    private int reserved(long balanceId) {
        return jdbcTemplate.queryForObject("select reserved from inventory_balances where id = ?", Integer.class, balanceId);
    }

    private int ledgerRows(long batchId) {
        return jdbcTemplate.queryForObject("select count(*) from inventory_transactions where batch_id = ?", Integer.class, batchId);
    }

    private int ledgerSum(long batchId) {
        return jdbcTemplate.queryForObject("select coalesce(sum(quantity_delta), 0) from inventory_transactions where batch_id = ?",
                Integer.class, batchId);
    }

    private String unitStatus(String serial) {
        return jdbcTemplate.queryForObject("select status from device_units where serial_number = ?", String.class, serial);
    }

    // ---------- Nhận hàng ----------

    @Test
    @DisplayName("Nhận hàng theo số lượng: tạo lô, dòng tồn và một dòng sổ INBOUND")
    void receiveQuantityCreatesBatchBalanceAndLedger() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);

        ReceiveResult result = receive(w, v, "LOT-1", 10, null);

        assertThat(onHand(result.balanceId())).isEqualTo(10);
        assertThat(reserved(result.balanceId())).isZero();
        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "select type, quantity_delta, warehouse_id from inventory_transactions where batch_id = ?", result.batchId());
        assertThat(ledger.get("type")).isEqualTo("INBOUND");
        assertThat(((Number) ledger.get("quantity_delta")).intValue()).isEqualTo(10);
        assertThat(((Number) ledger.get("warehouse_id")).longValue()).isEqualTo(w.warehouseId());
    }

    @Test
    @DisplayName("Nhận hàng theo serial: tạo từng thiết bị ở trạng thái IN_STOCK tại đúng vị trí")
    void receiveSerialCreatesDeviceUnits() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.SERIAL);
        String r = InventoryFixtures.random();

        receive(w, v, "LOT-S", 2, List.of("SN" + r + "1", "SN" + r + "2"));

        assertThat(unitStatus("SN" + r + "1")).isEqualTo("IN_STOCK");
        Long locationId = jdbcTemplate.queryForObject("select location_id from device_units where serial_number = ?",
                Long.class, "SN" + r + "2");
        assertThat(locationId).isEqualTo(w.locationId());
    }

    @Test
    @DisplayName("Dữ liệu nhận hàng sai bị từ chối và không để lại dữ liệu nào")
    void receiveValidation() {
        TestWarehouse w = fixtures.createWarehouse();
        TestWarehouse otherWarehouse = fixtures.createWarehouse();
        TestVariant quantityVariant = fixtures.createVariant(TrackingType.QUANTITY);
        TestVariant serialVariant = fixtures.createVariant(TrackingType.SERIAL);
        String r = InventoryFixtures.random();

        assertThatThrownBy(() -> receive(w, quantityVariant, "LOT-A", 0, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> receive(w, quantityVariant, "LOT-A", 5, List.of("X"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> receive(w, serialVariant, "LOT-B", 3, List.of("A" + r, "B" + r))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> receive(w, serialVariant, "LOT-B", 2, List.of("A" + r, "A" + r))).isInstanceOf(ConflictException.class);

        // Vị trí của kho khác, vị trí hàng lỗi
        ReceiveCommand wrongWarehouse = new ReceiveCommand(w.warehouseId(), otherWarehouse.locationId(), quantityVariant.variantId(),
                "LOT-C", null, null, BigDecimal.TEN, 0, 1, null, "TEST", 1L, null);
        assertThatThrownBy(() -> inventoryService.receive(wrongWarehouse)).isInstanceOf(ConflictException.class);

        ReceiveCommand defective = new ReceiveCommand(w.warehouseId(), fixtures.defectiveLocationId(w.warehouseId()),
                quantityVariant.variantId(), "LOT-D", null, null, BigDecimal.TEN, 0, 1, null, "TEST", 1L, null);
        assertThatThrownBy(() -> inventoryService.receive(defective)).isInstanceOf(ConflictException.class);

        // Sau tất cả các lỗi trên, chưa có lô nào được tạo
        Integer batches = jdbcTemplate.queryForObject(
                "select count(*) from inventory_batches where variant_id in (?, ?)", Integer.class,
                quantityVariant.variantId(), serialVariant.variantId());
        assertThat(batches).isZero();

        // Trùng mã lô và trùng serial với hàng đã có
        receive(w, quantityVariant, "LOT-OK", 1, null);
        assertThatThrownBy(() -> receive(w, quantityVariant, "LOT-OK", 1, null)).isInstanceOf(ConflictException.class);
        receive(w, serialVariant, "LOT-S1", 1, List.of("DUP" + r));
        assertThatThrownBy(() -> receive(w, serialVariant, "LOT-S2", 1, List.of("DUP" + r))).isInstanceOf(ConflictException.class);
    }

    // ---------- Giảm tồn ----------

    @Test
    @DisplayName("Giảm tồn: tồn giảm đúng và có một dòng sổ OUTBOUND âm")
    void decreaseReducesStockAndWritesLedger() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(w, v, "LOT-1", 10, null);

        out(result.balanceId(), 3, TransactionType.OUTBOUND, false, null);

        assertThat(onHand(result.balanceId())).isEqualTo(7);
        assertThat(ledgerRows(result.batchId())).isEqualTo(2);
        Integer delta = jdbcTemplate.queryForObject(
                "select quantity_delta from inventory_transactions where batch_id = ? and type = 'OUTBOUND'", Integer.class, result.batchId());
        assertThat(delta).isEqualTo(-3);
    }

    @Test
    @DisplayName("Xuất vượt tồn khả dụng bị từ chối, tồn không đổi và không có dòng sổ nào được ghi thêm")
    void decreaseMoreThanAvailableRollsBack() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(w, v, "LOT-1", 5, null);
        inventoryService.reserve(result.balanceId(), 2); // khả dụng còn 3

        assertThatThrownBy(() -> out(result.balanceId(), 4, TransactionType.OUTBOUND, false, null))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(onHand(result.balanceId())).isEqualTo(5);
        assertThat(reserved(result.balanceId())).isEqualTo(2);
        assertThat(ledgerRows(result.batchId())).isEqualTo(1); // chỉ còn dòng INBOUND
    }

    @Test
    @DisplayName("Giữ chỗ, hủy giữ chỗ và xuất từ phần đã giữ chỗ")
    void reserveReleaseAndDecreaseFromReserved() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(w, v, "LOT-1", 10, null);
        long id = result.balanceId();

        inventoryService.reserve(id, 4);
        assertThat(reserved(id)).isEqualTo(4);
        assertThatThrownBy(() -> inventoryService.reserve(id, 7)).isInstanceOf(InsufficientStockException.class); // khả dụng chỉ còn 6
        assertThatThrownBy(() -> inventoryService.release(id, 5)).isInstanceOf(BusinessException.class);        // chỉ đang giữ 4

        out(id, 4, TransactionType.OUTBOUND, true, null);   // xác nhận xuất phần đã giữ
        assertThat(onHand(id)).isEqualTo(6);
        assertThat(reserved(id)).isZero();
        assertThatThrownBy(() -> out(id, 1, TransactionType.OUTBOUND, true, null)).isInstanceOf(InsufficientStockException.class);

        inventoryService.reserve(id, 2);
        inventoryService.release(id, 2);
        assertThat(reserved(id)).isZero();
    }

    @Test
    @DisplayName("Xuất hàng theo serial: thiết bị chuyển sang SOLD và mất vị trí; serial sai hoặc đã bán bị từ chối")
    void serialDecreaseMarksUnitsSold() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.SERIAL);
        String r = InventoryFixtures.random();
        String s1 = "SN" + r + "1";
        String s2 = "SN" + r + "2";
        String s3 = "SN" + r + "3";
        ReceiveResult result = receive(w, v, "LOT-S", 3, List.of(s1, s2, s3));
        long id = result.balanceId();

        assertThatThrownBy(() -> out(id, 2, TransactionType.OUTBOUND, false, List.of(s1))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> out(id, 1, TransactionType.OUTBOUND, false, List.of("KHONG-CO"))).isInstanceOf(ConflictException.class);
        assertThat(onHand(id)).isEqualTo(3); // các lần lỗi trên không làm đổi gì

        out(id, 2, TransactionType.OUTBOUND, false, List.of(s1, s2));

        assertThat(onHand(id)).isEqualTo(1);
        assertThat(unitStatus(s1)).isEqualTo("SOLD");
        assertThat(unitStatus(s2)).isEqualTo("SOLD");
        assertThat(unitStatus(s3)).isEqualTo("IN_STOCK");
        Long location = jdbcTemplate.queryForObject("select location_id from device_units where serial_number = ?", Long.class, s1);
        assertThat(location).isNull();

        // Bán lại một serial đã bán
        assertThatThrownBy(() -> out(id, 1, TransactionType.OUTBOUND, false, List.of(s1))).isInstanceOf(ConflictException.class);
    }

    // ---------- Tăng tồn ----------

    @Test
    @DisplayName("Tăng tồn ở dòng đã có: chỉ các loại ADJUST, TRANSFER_IN, RMA_IN; hàng serial chưa hỗ trợ")
    void increaseRules() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant quantityVariant = fixtures.createVariant(TrackingType.QUANTITY);
        TestVariant serialVariant = fixtures.createVariant(TrackingType.SERIAL);
        ReceiveResult result = receive(w, quantityVariant, "LOT-1", 10, null);
        ReceiveResult serialResult = receive(w, serialVariant, "LOT-S", 1, List.of("SN" + InventoryFixtures.random()));

        inventoryService.increase(new StockInCommand(result.balanceId(), 5, TransactionType.ADJUST, "TEST", 3L, "Kiểm kê thừa"));
        assertThat(onHand(result.balanceId())).isEqualTo(15);

        assertThatThrownBy(() -> inventoryService.increase(
                new StockInCommand(result.balanceId(), 1, TransactionType.INBOUND, "TEST", 3L, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inventoryService.increase(
                new StockInCommand(serialResult.balanceId(), 1, TransactionType.ADJUST, "TEST", 3L, null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- Sổ giao dịch ----------

    @Test
    @DisplayName("Tổng các dòng sổ luôn bằng tồn thực tế sau một chuỗi thao tác")
    void ledgerAlwaysMatchesStock() {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(w, v, "LOT-1", 10, null);
        long id = result.balanceId();

        out(id, 2, TransactionType.OUTBOUND, false, null);
        inventoryService.increase(new StockInCommand(id, 3, TransactionType.ADJUST, "TEST", 4L, null));
        out(id, 1, TransactionType.ADJUST, false, null);
        assertThatThrownBy(() -> out(id, 100, TransactionType.OUTBOUND, false, null)).isInstanceOf(InsufficientStockException.class);

        assertThat(onHand(id)).isEqualTo(10);                      // 10 - 2 + 3 - 1
        assertThat(ledgerSum(result.batchId())).isEqualTo(onHand(id));
    }

    // ---------- Tranh chấp đồng thời ----------

    /** Kết quả của một đợt nhiều luồng cùng chạy một thao tác. */
    private record Outcome(int success, int insufficient, int other) {
    }

    /** Cho nhiều luồng chờ ở vạch xuất phát rồi cùng chạy một lúc, để tạo tranh chấp thật. */
    private Outcome runConcurrently(int threads, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                    success.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<Object> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return new Outcome(success.get(), insufficient.get(), other.get());
    }

    @Test
    @DisplayName("20 luồng cùng xuất 1 sản phẩm từ lô có 10: đúng 10 thành công, tồn về 0 và không bao giờ âm")
    void concurrentDecreaseNeverGoesNegative() throws Exception {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(w, v, "LOT-1", 10, null);

        Outcome outcome = runConcurrently(20, () -> out(result.balanceId(), 1, TransactionType.OUTBOUND, false, null));

        assertThat(outcome.success()).isEqualTo(10);
        assertThat(outcome.insufficient()).isEqualTo(10);
        assertThat(outcome.other()).isZero();
        assertThat(onHand(result.balanceId())).isZero();
        assertThat(ledgerRows(result.batchId())).isEqualTo(11);   // 1 INBOUND + 10 OUTBOUND
        assertThat(ledgerSum(result.batchId())).isZero();          // sổ khớp tồn
    }

    @Test
    @DisplayName("20 luồng cùng giữ chỗ 1 sản phẩm từ lô có 10: đúng 10 thành công, số giữ chỗ không vượt tồn")
    void concurrentReserveNeverExceedsOnHand() throws Exception {
        TestWarehouse w = fixtures.createWarehouse();
        TestVariant v = fixtures.createVariant(TrackingType.QUANTITY);
        ReceiveResult result = receive(w, v, "LOT-1", 10, null);

        Outcome outcome = runConcurrently(20, () -> inventoryService.reserve(result.balanceId(), 1));

        assertThat(outcome.success()).isEqualTo(10);
        assertThat(outcome.insufficient()).isEqualTo(10);
        assertThat(outcome.other()).isZero();
        assertThat(reserved(result.balanceId())).isEqualTo(10);
        assertThat(onHand(result.balanceId())).isEqualTo(10);
    }
}
