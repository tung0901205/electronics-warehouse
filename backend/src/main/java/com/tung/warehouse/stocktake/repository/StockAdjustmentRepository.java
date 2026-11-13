package com.tung.warehouse.stocktake.repository;

import com.tung.warehouse.stocktake.entity.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
}
