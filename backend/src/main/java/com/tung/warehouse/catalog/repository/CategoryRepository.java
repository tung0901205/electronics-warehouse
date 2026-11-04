package com.tung.warehouse.catalog.repository;

import com.tung.warehouse.catalog.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();

    /** Danh mục này có danh mục con không? (parent.id = ?) */
    boolean existsByParentId(Long parentId);

    /**
     * Đã có danh mục cùng tên trong cùng một danh mục cha chưa?
     * parentId = null nghĩa là kiểm tra trong nhóm danh mục gốc (Spring tự đổi thành "parent_id IS NULL").
     */
    boolean existsByNameIgnoreCaseAndParentId(String name, Long parentId);

    boolean existsByNameIgnoreCaseAndParentIdAndIdNot(String name, Long parentId, Long id);
}
