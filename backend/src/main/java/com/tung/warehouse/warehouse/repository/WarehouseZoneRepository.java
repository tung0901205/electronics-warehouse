package com.tung.warehouse.warehouse.repository;

import com.tung.warehouse.warehouse.entity.WarehouseZone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseZoneRepository extends JpaRepository<WarehouseZone, Long> {

    List<WarehouseZone> findByWarehouseIdOrderByCodeAsc(Long warehouseId);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);
}
