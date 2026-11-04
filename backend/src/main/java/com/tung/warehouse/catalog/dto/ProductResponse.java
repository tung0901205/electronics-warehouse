package com.tung.warehouse.catalog.dto;

import com.tung.warehouse.catalog.entity.Product;
import java.time.LocalDateTime;
import java.util.List;

/** Sản phẩm kèm danh sách biến thể. */
public record ProductResponse(
        Long id,
        String name,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        String description,
        String status,
        List<VariantResponse> variants,
        LocalDateTime createdAt) {

    public static ProductResponse from(Product product) {
        Long brandId = product.getBrand() == null ? null : product.getBrand().getId();
        String brandName = product.getBrand() == null ? null : product.getBrand().getName();
        List<VariantResponse> variants = product.getVariants().stream().map(VariantResponse::from).toList();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                brandId,
                brandName,
                product.getDescription(),
                product.getStatus().name(),
                variants,
                product.getCreatedAt());
    }
}
