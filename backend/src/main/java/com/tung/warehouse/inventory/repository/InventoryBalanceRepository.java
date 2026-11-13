package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.InventoryBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    /**
     * Danh sách id các dòng tồn còn hàng khả dụng của một biến thể trong một kho, theo thứ tự FIFO:
     * lô nhận trước đứng trước. Câu này KHÔNG khóa; service sẽ khóa từng dòng theo đúng thứ tự này
     * rồi đọc lại số liệu mới nhất. Loại bỏ lô bị khóa (BLOCKED) và vị trí hàng lỗi (DEFECTIVE).
     * [SỬA Giai đoạn 6] Viết bằng SQL thuần để nối được với bảng vị trí của module warehouse.
     */
    @Query(value = """
            SELECT b.id FROM inventory_balances b
              JOIN inventory_batches ib ON ib.id = b.batch_id
              JOIN storage_locations l ON l.id = b.location_id
             WHERE b.warehouse_id = :warehouseId AND b.variant_id = :variantId
               AND b.on_hand > b.reserved
               AND ib.status = 'AVAILABLE'
               AND l.location_type <> 'DEFECTIVE'
             ORDER BY ib.received_at ASC, b.id ASC
            """, nativeQuery = true)
    List<Number> findFifoBalanceIds(@Param("warehouseId") Long warehouseId, @Param("variantId") Long variantId);

    /** [MỚI Giai đoạn 6] Tìm id dòng tồn theo đúng tổ hợp (kho, vị trí, biến thể, lô). Rỗng nếu chưa có. */
    @Query("""
            select b.id from InventoryBalance b
             where b.warehouseId = :warehouseId and b.locationId = :locationId
               and b.variantId = :variantId and b.batch.id = :batchId
            """)
    Optional<Long> findIdByKey(@Param("warehouseId") Long warehouseId, @Param("locationId") Long locationId,
                               @Param("variantId") Long variantId, @Param("batchId") Long batchId);

    /**
     * Đọc một dòng tồn VÀ KHÓA dòng đó (PESSIMISTIC_WRITE = SELECT ... FOR UPDATE).
     * Từ lúc này đến khi transaction kết thúc, mọi người khác muốn đọc-để-sửa dòng này phải chờ.
     * Đây là cách chống "hai người cùng xuất số hàng cuối cùng". Chỉ dùng được bên trong @Transactional.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.id = :id")
    Optional<InventoryBalance> findByIdForUpdate(@Param("id") Long id);
}
