package com.tung.warehouse.catalog.dto;

import com.tung.warehouse.catalog.entity.Brand;

public record BrandResponse(Long id, String name) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName());
    }
}
