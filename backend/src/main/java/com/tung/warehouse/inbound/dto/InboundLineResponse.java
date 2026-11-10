package com.tung.warehouse.inbound.dto;

import java.math.BigDecimal;
import java.util.List;

public record InboundLineResponse(
        Long id,
        Long variantId,
        String sku,
        String trackingType,
        Long locationId,
        String locationCode,
        int quantity,
        BigDecimal unitCost,
        String batchCode,
        int warrantyMonths,
        List<String> serials) {
}
