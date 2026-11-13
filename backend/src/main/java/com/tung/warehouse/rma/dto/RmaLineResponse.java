package com.tung.warehouse.rma.dto;

public record RmaLineResponse(Long id, Long variantId, String sku, String serialNumber, String condition, String resolution, String note) {
}
