package com.tung.warehouse.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tung.warehouse.common.exception.BusinessException;
import com.tung.warehouse.common.exception.InsufficientStockException;
import com.tung.warehouse.inventory.entity.InventoryBalance;
import com.tung.warehouse.inventory.entity.InventoryBatch;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test thuần cho quy tắc tồn kho trong entity InventoryBalance.
 * Không khởi động Spring, không cần database nên chạy trong vài mili giây.
 */
class InventoryBalanceTest {

    /** Tạo một dòng tồn có 10 sản phẩm thực tế, chưa giữ chỗ. */
    private InventoryBalance balanceWithTen() {
        InventoryBatch batch = new InventoryBatch(1L, "LOT-1", null, null, LocalDateTime.now(), new BigDecimal("100.00"), 12);
        return new InventoryBalance(1L, 1L, 1L, batch, 10);
    }

    @Test
    @DisplayName("Tồn khả dụng bằng tồn thực tế trừ số đã giữ chỗ")
    void availableIsOnHandMinusReserved() {
        InventoryBalance balance = balanceWithTen();
        assertThat(balance.getAvailable()).isEqualTo(10);

        balance.reserve(4);

        assertThat(balance.getOnHand()).isEqualTo(10);
        assertThat(balance.getReserved()).isEqualTo(4);
        assertThat(balance.getAvailable()).isEqualTo(6);
    }

    @Test
    @DisplayName("Giảm tồn từ phần khả dụng; xin nhiều hơn khả dụng thì bị từ chối và số liệu không đổi")
    void decreaseCannotExceedAvailable() {
        InventoryBalance balance = balanceWithTen();
        balance.reserve(4); // khả dụng còn 6

        balance.decrease(6, false);
        assertThat(balance.getOnHand()).isEqualTo(4);

        assertThatThrownBy(() -> balance.decrease(1, false))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(balance.getOnHand()).isEqualTo(4);   // không bị âm, không đổi
        assertThat(balance.getReserved()).isEqualTo(4);
    }

    @Test
    @DisplayName("Giữ chỗ không được vượt tồn khả dụng")
    void reserveCannotExceedAvailable() {
        InventoryBalance balance = balanceWithTen();

        assertThatThrownBy(() -> balance.reserve(11)).isInstanceOf(InsufficientStockException.class);

        balance.reserve(10);
        assertThat(balance.getAvailable()).isZero();
        assertThatThrownBy(() -> balance.reserve(1)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("Giảm từ phần đã giữ chỗ: trừ cả tồn thực tế và số giữ chỗ")
    void decreaseFromReserved() {
        InventoryBalance balance = balanceWithTen();
        balance.reserve(4);

        balance.decrease(4, true);

        assertThat(balance.getOnHand()).isEqualTo(6);
        assertThat(balance.getReserved()).isZero();
        assertThatThrownBy(() -> balance.decrease(1, true)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("Hủy giữ chỗ không được nhiều hơn số đang giữ")
    void releaseCannotExceedReserved() {
        InventoryBalance balance = balanceWithTen();
        balance.reserve(3);

        assertThatThrownBy(() -> balance.release(4)).isInstanceOf(BusinessException.class);

        balance.release(3);
        assertThat(balance.getReserved()).isZero();
        assertThat(balance.getAvailable()).isEqualTo(10);
    }

    @Test
    @DisplayName("Tăng tồn cộng thêm vào tồn thực tế")
    void increaseAddsToOnHand() {
        InventoryBalance balance = balanceWithTen();
        balance.increase(5);
        assertThat(balance.getOnHand()).isEqualTo(15);
        assertThat(balance.getAvailable()).isEqualTo(15);
    }
}
