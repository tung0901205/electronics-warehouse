package com.tung.warehouse.warehouse.dto;

import com.tung.warehouse.warehouse.entity.StorageLocation;

/** [SỬA Giai đoạn 3] Thêm warehouseId ở cuối để biết vị trí thuộc kho nào. */
public record LocationResponse(Long id, Long zoneId, String zoneCode, String code, String locationType,
                               String status, Long warehouseId) {

    public static LocationResponse from(StorageLocation location) {
        return new LocationResponse(location.getId(), location.getZone().getId(), location.getZone().getCode(),
                location.getCode(), location.getLocationType().name(), location.getStatus().name(),
                location.getZone().getWarehouse().getId());
    }
}
