package com.tung.warehouse.stocktake.dto;

public record StocktakeLineResponse(
        Long id,
        Long balanceId,
        String sku,
        String productName,
        String locationCode,
        String batchCode,
        int systemQuantity,
        Integer countedQuantity,
        int difference,
        String reason) {
}
