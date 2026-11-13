package com.tung.warehouse.reporting.dto;

import java.util.List;

/** Điều kiện chung của các báo cáo. restrictTo: null là không giới hạn kho (Admin), danh sách là các kho được xem. */
public record ReportFilter(Long warehouseId, String keyword, List<Long> restrictTo) {
}
