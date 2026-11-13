package com.tung.warehouse.rma.dto;

import com.tung.warehouse.rma.entity.RmaCondition;
import com.tung.warehouse.rma.entity.RmaResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Kết quả kiểm tra và cách xử lý một thiết bị.
 * GOOD: resolution phải là RESTOCKED và phải chọn locationId (vị trí thường) để nhập lại kho.
 * DEFECTIVE: resolution là RETURNED_TO_SUPPLIER hoặc SCRAPPED; hàng tự đưa vào vị trí hàng lỗi của kho, không cần locationId.
 */
public record RmaProcessLine(
        @NotNull(message = "Thiếu mã dòng") Long lineId,
        @NotNull(message = "Phải chọn tình trạng (GOOD hoặc DEFECTIVE)") RmaCondition condition,
        @NotNull(message = "Phải chọn cách xử lý") RmaResolution resolution,
        Long locationId,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
}
