package com.tung.warehouse.warehouse.service;

import com.tung.warehouse.common.exception.WarehouseAccessDeniedException;
import com.tung.warehouse.common.security.CurrentUser;
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
}
