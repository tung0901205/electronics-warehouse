package com.tung.warehouse.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OutboundAllocationResponse(
        Long batchId,
        String batchCode,
        String serialNumber,
        Long locationId,
        String locationCode,
        int quantity,
        BigDecimal unitCost,
        LocalDate warrantyEndDate) {
}
