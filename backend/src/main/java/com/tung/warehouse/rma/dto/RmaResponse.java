package com.tung.warehouse.rma.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RmaResponse(Long id, String code, Long warehouseId, Long customerId, Long outboundOrderId,
                          String status, String reason, LocalDateTime createdAt, List<RmaLineResponse> lines) {
}
