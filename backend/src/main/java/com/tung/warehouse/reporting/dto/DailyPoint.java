package com.tung.warehouse.reporting.dto;

import java.time.LocalDate;

/** Số lượng nhập và xuất trong một ngày (dùng cho biểu đồ trên dashboard). */
public record DailyPoint(LocalDate date, long qtyIn, long qtyOut) {
}
