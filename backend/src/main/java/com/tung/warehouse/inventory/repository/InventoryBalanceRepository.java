package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.InventoryBalance;
import jakarta.persistence.LockModeType;
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.id = :id")
    Optional<InventoryBalance> findByIdForUpdate(@Param("id") Long id);
}
