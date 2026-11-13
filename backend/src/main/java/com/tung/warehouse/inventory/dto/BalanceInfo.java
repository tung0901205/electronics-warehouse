package com.tung.warehouse.inventory.dto;

/** Thông tin để hiển thị một dòng tồn: SKU, tên sản phẩm, mã vị trí, mã lô. */
public record BalanceInfo(Long balanceId, Long variantId, String sku, String productName, String locationCode, String batchCode) {
}
