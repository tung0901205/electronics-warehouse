package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    /** Biến thể này đã có lô mang mã này chưa? (mã lô là duy nhất trong từng biến thể) */
    boolean existsByVariantIdAndBatchCode(Long variantId, String batchCode);
}
