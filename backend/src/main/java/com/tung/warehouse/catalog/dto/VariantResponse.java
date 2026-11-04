package com.tung.warehouse.catalog.dto;

import com.tung.warehouse.catalog.entity.ProductVariant;
import java.math.BigDecimal;

public record VariantResponse(
        Long id,
        Long productId,
        String sku,
        String barcode,
        String attributes,
        Long unitId,
        String unitCode,
        String trackingType,
        BigDecimal listPrice,
        int warrantyMonths,
        String status) {

    public static VariantResponse from(ProductVariant variant) {
        return new VariantResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getAttributes(),
                variant.getUnit().getId(),
                variant.getUnit().getCode(),
                variant.getTrackingType().name(),
                variant.getListPrice(),
                variant.getWarrantyMonths(),
                variant.getStatus().name());
    }
}
