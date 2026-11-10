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
     * Đọc một dòng tồn VÀ KHÓA dòng đó (PESSIMISTIC_WRITE = SELECT ... FOR UPDATE).
     * Từ lúc này đến khi transaction kết thúc, mọi người khác muốn đọc-để-sửa dòng này phải chờ.
     * Đây là cách chống "hai người cùng xuất số hàng cuối cùng". Chỉ dùng được bên trong @Transactional.
     */
    /**
     * [MỚI Giai đoạn 5] Danh sách id các dòng tồn còn hàng khả dụng của một biến thể trong một kho,
     * theo thứ tự FIFO: lô nhận trước đứng trước. Câu này KHÔNG khóa; service sẽ khóa từng dòng theo đúng thứ tự này
     * rồi đọc lại số liệu mới nhất. Lô bị khóa (BLOCKED) bị loại.
     */
    @Query("""
            select b.id from InventoryBalance b
             where b.warehouseId = :warehouseId and b.variantId = :variantId
               and b.onHand > b.reserved
               and b.batch.status = com.tung.warehouse.inventory.entity.BatchStatus.AVAILABLE
             order by b.batch.receivedAt asc, b.id asc
            """)
    List<Long> findFifoBalanceIds(@Param("warehouseId") Long warehouseId, @Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.id = :id")
    Optional<InventoryBalance> findByIdForUpdate(@Param("id") Long id);
}
