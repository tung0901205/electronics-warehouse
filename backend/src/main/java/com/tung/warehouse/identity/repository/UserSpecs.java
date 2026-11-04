package com.tung.warehouse.identity.repository;

import com.tung.warehouse.identity.entity.User;
import org.springframework.data.jpa.domain.Specification;

/**
 * Các điều kiện tìm kiếm người dùng. Mỗi hàm là MỘT điều kiện, trả về một Specification.
 * Service ghép chúng lại bằng Specification.where(...).and(...).and(...).
 *
 * Quy ước: tham số rỗng thì return null, nghĩa là "bỏ qua điều kiện này".
 * root = bảng đang tìm (users), query = câu truy vấn, cb = công cụ tạo điều kiện (like, equal, or...).
 */
public final class UserSpecs {

    private UserSpecs() {
    }

    // 1. Tìm mờ theo từ khóa: tên đăng nhập, họ tên hoặc email
    public static Specification<User> keywordContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            String pattern = "%" + keyword.trim().toLowerCase() + "%"; // lower để không phân biệt HOA/thường
            return cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern));
        };
    }

    // 2. Lọc theo vai trò. root.join("roles") nối sang bảng roles (qua user_roles)
    public static Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> {
            if (roleName == null || roleName.isBlank()) return null; // return TRƯỚC khi join, nếu không join sẽ loại user không có vai trò
            return cb.equal(root.join("roles").get("name"), roleName.trim().toUpperCase());
        };
    }

    // 3. Lọc theo trạng thái: đang hoạt động (true) hoặc đã vô hiệu hóa (false)
    public static Specification<User> hasEnabled(Boolean enabled) {
        return (root, query, cb) -> {
            if (enabled == null) return null;
            return cb.equal(root.get("enabled"), enabled);
        };
    }
}
