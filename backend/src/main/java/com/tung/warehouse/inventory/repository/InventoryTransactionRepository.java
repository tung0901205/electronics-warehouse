package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sổ giao dịch chỉ THÊM: code chỉ được dùng save(...), không dùng sửa hay xóa. */
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
}
