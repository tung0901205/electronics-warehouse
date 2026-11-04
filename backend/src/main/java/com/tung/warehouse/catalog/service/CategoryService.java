package com.tung.warehouse.catalog.service;

import com.tung.warehouse.catalog.dto.CategoryRequest;
import com.tung.warehouse.catalog.dto.CategoryResponse;
import com.tung.warehouse.catalog.entity.Category;
import com.tung.warehouse.catalog.repository.CategoryRepository;
import com.tung.warehouse.catalog.repository.ProductRepository;
import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.2 (phần danh mục) và UC2.5 (tra cứu). */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** Danh sách phẳng, giao diện tự dựng cây từ parentId. Số danh mục ít nên không cần phân trang. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return CategoryResponse.from(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        Category parent = findParent(request.parentId());
        if (categoryRepository.existsByNameIgnoreCaseAndParentId(name, request.parentId())) {
            throw new ConflictException("Đã có danh mục tên \"" + name + "\" trong danh mục cha này");
        }
        return CategoryResponse.from(categoryRepository.save(new Category(name, parent)));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        String name = request.name().trim();
        Category newParent = findParent(request.parentId());

        checkNoCycle(category, newParent);
        if (categoryRepository.existsByNameIgnoreCaseAndParentIdAndIdNot(name, request.parentId(), id)) {
            throw new ConflictException("Đã có danh mục tên \"" + name + "\" trong danh mục cha này");
        }
        category.update(name, newParent);
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active) {
        Category category = findOrThrow(id);
        if (active) {
            category.activate();
        } else {
            category.deactivate();
        }
        return CategoryResponse.from(category);
    }

    /** Chỉ xóa được danh mục trống: không có danh mục con và không có sản phẩm. */
    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Không thể xóa: danh mục còn danh mục con");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Không thể xóa: danh mục đang có sản phẩm");
        }
        categoryRepository.delete(category);
    }

    /**
     * Không cho chọn chính nó hoặc danh mục con/cháu của nó làm danh mục cha, nếu không cây sẽ bị vòng tròn.
     * Cách kiểm tra: đi ngược từ danh mục cha mới lên tận gốc; nếu gặp chính danh mục đang sửa thì là vòng.
     */
    private void checkNoCycle(Category category, Category newParent) {
        Category cursor = newParent;
        while (cursor != null) {
            if (cursor.getId().equals(category.getId())) {
                throw new ConflictException("Không thể chọn chính nó hoặc danh mục con của nó làm danh mục cha");
            }
            cursor = cursor.getParent();
        }
    }

    private Category findParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return findOrThrow(parentId);
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Danh mục", id));
    }
}
