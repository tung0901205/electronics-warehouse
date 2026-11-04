package com.tung.warehouse.warehouse.repository;

import com.tung.warehouse.warehouse.entity.StorageLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    /** Tất cả vị trí của một kho. "ZoneWarehouseId" nghĩa là đi từ vị trí sang khu vực rồi sang kho: zone.warehouse.id. */
    List<StorageLocation> findByZoneWarehouseIdOrderByCodeAsc(Long warehouseId);

    boolean existsByZoneIdAndCode(Long zoneId, String code);

    boolean existsByZoneId(Long zoneId);
}
