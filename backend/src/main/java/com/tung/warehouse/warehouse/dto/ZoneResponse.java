package com.tung.warehouse.warehouse.dto;

import com.tung.warehouse.warehouse.entity.WarehouseZone;

public record ZoneResponse(Long id, Long warehouseId, String code, String name) {

    public static ZoneResponse from(WarehouseZone zone) {
        return new ZoneResponse(zone.getId(), zone.getWarehouse().getId(), zone.getCode(), zone.getName());
    }
}
