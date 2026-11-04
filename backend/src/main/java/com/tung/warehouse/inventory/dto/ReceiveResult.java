package com.tung.warehouse.inventory.dto;

/** Kết quả nhận hàng: id lô và id dòng tồn vừa tạo. */
public record ReceiveResult(Long batchId, Long balanceId) {
}
