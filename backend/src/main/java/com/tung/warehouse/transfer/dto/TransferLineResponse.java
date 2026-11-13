package com.tung.warehouse.transfer.dto;

import java.util.List;

public record TransferLineResponse(
        Long id,
        Long variantId,
        String sku,
        String trackingType,
        Long batchId,
        int quantity,
        Integer receivedQuantity,
        Long destLocationId,
        List<String> serials,
        List<String> receivedSerials) {
}
