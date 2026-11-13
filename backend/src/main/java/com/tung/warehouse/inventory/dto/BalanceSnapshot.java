package com.tung.warehouse.inventory.dto;

/** Số liệu hiện tại của một dòng tồn. */
public record BalanceSnapshot(int onHand, long version) {
}
