package com.tung.warehouse.inbound.repository;

import com.tung.warehouse.inbound.entity.InboundOrder;
import com.tung.warehouse.inbound.entity.InboundStatus;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Các điều kiện tìm kiếm phiếu nhập. Service ghép bằng where(...).and(...).and(...).
 * Tham số rỗng thì return null, nghĩa là "bỏ qua điều kiện này".
 */
public final class InboundSpecs {

    private InboundSpecs() {
    }

    // 1. Tìm mờ theo mã phiếu
    public static Specification<InboundOrder> codeContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            return cb.like(cb.lower(root.get("code")), "%" + keyword.trim().toLowerCase() + "%");
        };
    }

    // 2. Lọc theo trạng thái
    public static Specification<InboundOrder> hasStatus(InboundStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    // 3. Lọc theo một kho
    public static Specification<InboundOrder> hasWarehouse(Long warehouseId) {
        return (root, query, cb) -> {
            if (warehouseId == null) return null;
            return cb.equal(root.get("warehouseId"), warehouseId);
        };
    }

    // 4. Giới hạn theo danh sách kho được xem (null = Admin, không giới hạn)
    public static Specification<InboundOrder> warehouseIn(List<Long> warehouseIds) {
        return (root, query, cb) -> {
            if (warehouseIds == null) return null;
            if (warehouseIds.isEmpty()) return cb.disjunction(); // không được gán kho nào: không thấy phiếu nào
            return root.get("warehouseId").in(warehouseIds);
        };
    }
}
