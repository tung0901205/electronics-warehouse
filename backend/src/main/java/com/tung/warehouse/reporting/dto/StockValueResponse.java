package com.tung.warehouse.reporting.dto;

import com.tung.warehouse.common.response.PageResponse;
import java.math.BigDecimal;

/** Báo cáo giá trị tồn: tổng giá trị (của toàn bộ kết quả lọc, không chỉ trang hiện tại) và các dòng chi tiết. */
public record StockValueResponse(BigDecimal totalValue, PageResponse<StockValueRow> page) {
}
