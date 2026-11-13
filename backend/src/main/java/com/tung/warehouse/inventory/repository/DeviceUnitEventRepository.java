package com.tung.warehouse.inventory.repository;

import com.tung.warehouse.inventory.entity.DeviceUnitEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ dùng save(...). Việc đọc lịch sử dùng SQL trong InventoryQueryRepository. */
public interface DeviceUnitEventRepository extends JpaRepository<DeviceUnitEvent, Long> {
}
