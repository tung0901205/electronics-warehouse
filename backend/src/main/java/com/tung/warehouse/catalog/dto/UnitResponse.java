package com.tung.warehouse.catalog.dto;

import com.tung.warehouse.catalog.entity.Unit;

public record UnitResponse(Long id, String code, String name) {

    public static UnitResponse from(Unit unit) {
        return new UnitResponse(unit.getId(), unit.getCode(), unit.getName());
    }
}
