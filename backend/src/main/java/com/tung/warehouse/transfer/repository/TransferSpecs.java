package com.tung.warehouse.transfer.repository;

import com.tung.warehouse.transfer.entity.TransferOrder;
import com.tung.warehouse.transfer.entity.TransferStatus;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Các điều kiện tìm kiếm phiếu điều chuyển. Service ghép bằng where(...).and(...).and(...).
 * Tham số rỗng thì return null, nghĩa là "bỏ qua điều kiện này".
 */
public final class TransferSpecs {

    private TransferSpecs() {
    }

    // 1. Tìm mờ theo mã phiếu
    public static Specification<TransferOrder> codeContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            return cb.like(cb.lower(root.get("code")), "%" + keyword.trim().toLowerCase() + "%");
        };
    }

    // 2. Lọc theo trạng thái
    public static Specification<TransferOrder> hasStatus(TransferStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    // 3. Phiếu liên quan tới một kho (là kho nguồn hoặc kho đích)
    public static Specification<TransferOrder> involvesWarehouse(Long warehouseId) {
        return (root, query, cb) -> {
            if (warehouseId == null) return null;
            return cb.or(cb.equal(root.get("sourceWarehouseId"), warehouseId), cb.equal(root.get("destWarehouseId"), warehouseId));
        };
    }

    // 4. Giới hạn theo danh sách kho được xem: thấy phiếu nếu được vào kho nguồn hoặc kho đích (null = Admin)
    public static Specification<TransferOrder> involvesAnyOf(List<Long> warehouseIds) {
        return (root, query, cb) -> {
            if (warehouseIds == null) return null;
            if (warehouseIds.isEmpty()) return cb.disjunction();
            return cb.or(root.get("sourceWarehouseId").in(warehouseIds), root.get("destWarehouseId").in(warehouseIds));
        };
    }
}
