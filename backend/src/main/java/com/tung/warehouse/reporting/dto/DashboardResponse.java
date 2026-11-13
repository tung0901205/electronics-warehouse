package com.tung.warehouse.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Số liệu tổng quan cho dashboard. totalValue chỉ có với Quản lý kho trở lên (giá vốn là thông tin nhạy cảm),
 * người khác nhận null.
 */
public record DashboardResponse(
        int warehouses,
        long variantsInStock,
        long totalOnHand,
        BigDecimal totalValue,
        long lowStockCount,
        long agingCount,
        long pendingInbound,
        long pendingOutbound,
        long pendingTransfers,
        List<DailyPoint> last7Days) {
}
