package com.tung.warehouse.outbound.dto;

import java.math.BigDecimal;
import java.util.List;

public record OutboundLineResponse(
        Long id,
        Long variantId,
        String sku,
        String trackingType,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal costAmount,
        List<OutboundAllocationResponse> allocations) {
}
