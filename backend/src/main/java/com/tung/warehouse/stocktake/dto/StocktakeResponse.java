package com.tung.warehouse.stocktake.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StocktakeResponse(
        Long id,
        String code,
        Long warehouseId,
        String scope,
        String status,
        Long countedBy,
        Long approvedBy,
        LocalDateTime startedAt,
        LocalDateTime appliedAt,
        LocalDateTime createdAt,
        List<StocktakeLineResponse> lines) {
}
