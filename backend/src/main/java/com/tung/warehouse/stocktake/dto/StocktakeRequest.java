package com.tung.warehouse.stocktake.dto;

import com.tung.warehouse.stocktake.entity.StocktakeScope;
import jakarta.validation.constraints.NotNull;

/** Tạo đợt kiểm kê. scope = ZONE thì phải có zoneId; scope = PRODUCT thì phải có variantId. */
public record StocktakeRequest(
        @NotNull(message = "Phải chọn kho") Long warehouseId,
        @NotNull(message = "Phải chọn phạm vi (WAREHOUSE, ZONE hoặc PRODUCT)") StocktakeScope scope,
        Long zoneId,
        Long variantId) {
}
