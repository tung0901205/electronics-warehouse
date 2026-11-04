package com.tung.warehouse.catalog.repository;

import com.tung.warehouse.catalog.entity.CatalogStatus;
import com.tung.warehouse.catalog.entity.Product;
import org.springframework.data.jpa.domain.Specification;

/**
 * Các điều kiện tìm kiếm sản phẩm. Mỗi hàm là MỘT điều kiện; service ghép bằng where(...).and(...).and(...).
 * Tham số rỗng thì return null, nghĩa là "bỏ qua điều kiện này".
 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    // 1. Tìm mờ theo tên sản phẩm
    public static Specification<Product> nameContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            return cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase() + "%"); // lower để không phân biệt HOA/thường
        };
    }

    // 2. Lọc theo danh mục. root.get("category").get("id") = đi từ sản phẩm sang danh mục rồi lấy id
    public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) return null;
            return cb.equal(root.get("category").get("id"), categoryId);
        };
    }

    // 3. Lọc theo trạng thái
    public static Specification<Product> hasStatus(CatalogStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }
}
