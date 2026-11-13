package com.tung.warehouse.inventory.dto;

/** Một dòng tồn có thể đưa vào kiểm kê: số liệu hiện tại và phiên bản (để phát hiện thay đổi sau này). */
public record CountableBalance(Long balanceId, int onHand, int reserved, long version) {
}
