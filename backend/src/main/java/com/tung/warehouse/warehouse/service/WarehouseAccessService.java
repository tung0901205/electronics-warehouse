package com.tung.warehouse.warehouse.service;

import com.tung.warehouse.common.exception.WarehouseAccessDeniedException;
import com.tung.warehouse.common.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Nơi DUY NHẤT kiểm tra "người này có được làm việc ở kho này không".
 * Các module khác (nhập kho, xuất kho, điều chuyển...) sẽ gọi lại lớp này, khỏi viết lặp.
 * Quy tắc: Admin không bị giới hạn; người khác chỉ được vào các kho đã được gán.
 */
@Service
public class WarehouseAccessService {

    public boolean canAccess(Long warehouseId) {
        return CurrentUser.isAdmin() || CurrentUser.warehouseIds().contains(warehouseId);
    }

    public void assertCanAccess(Long warehouseId) {
        if (!canAccess(warehouseId)) {
            throw new WarehouseAccessDeniedException();
        }
    }

    /**
     * [MỚI Giai đoạn 7] Phạm vi kho được xem khi tra cứu hoặc báo cáo:
     *   - có chọn một kho cụ thể: phải được phép vào kho đó (nếu không báo 403), trả về null (không cần giới hạn thêm)
     *   - Admin: trả về null (không giới hạn)
     *   - người khác: danh sách các kho được gán (có thể rỗng)
     */
    public List<Long> restrictTo(Long warehouseId) {
        if (warehouseId != null) {
            assertCanAccess(warehouseId);
            return null;
        }
        if (CurrentUser.isAdmin()) {
            return null;
        }
        return new ArrayList<>(CurrentUser.warehouseIds());
    }
}
