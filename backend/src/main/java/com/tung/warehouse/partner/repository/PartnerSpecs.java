package com.tung.warehouse.partner.repository;

import com.tung.warehouse.partner.entity.Partner;
import com.tung.warehouse.partner.entity.PartnerStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Các điều kiện tìm kiếm nhà cung cấp và khách hàng. Dùng chung cho cả hai vì cùng kế thừa Partner.
 * Mỗi hàm là MỘT điều kiện; service ghép bằng Specification.where(...).and(...).and(...).
 * Tham số rỗng thì return null, nghĩa là "bỏ qua điều kiện này".
 */
public final class PartnerSpecs {

    private PartnerSpecs() {
    }

    // 1. Tìm mờ theo mã hoặc tên
    public static <T extends Partner> Specification<T> keywordContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            String pattern = "%" + keyword.trim().toLowerCase() + "%"; // lower để không phân biệt HOA/thường
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern));
        };
    }

    // 2. Tìm mờ theo số điện thoại
    public static <T extends Partner> Specification<T> phoneContains(String phone) {
        return (root, query, cb) -> {
            if (phone == null || phone.isBlank()) return null;
            return cb.like(root.get("phone"), "%" + phone.trim() + "%");
        };
    }

    // 3. Lọc theo trạng thái
    public static <T extends Partner> Specification<T> hasStatus(PartnerStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }
}
