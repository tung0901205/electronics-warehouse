package com.tung.warehouse.warehouse.dto;

import com.tung.warehouse.warehouse.entity.Warehouse;

public record WarehouseResponse(Long id, String code, String name, String address, String status) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                warehouse.getAddress(), warehouse.getStatus().name());
    }
}
