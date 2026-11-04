package com.tung.warehouse.catalog.dto;

import com.tung.warehouse.catalog.entity.Category;

/** Trả danh sách phẳng kèm parentId; giao diện tự dựng thành cây. */
public record CategoryResponse(Long id, String name, Long parentId, String status) {

    public static CategoryResponse from(Category category) {
        Long parentId = category.getParent() == null ? null : category.getParent().getId();
        return new CategoryResponse(category.getId(), category.getName(), parentId, category.getStatus().name());
    }
}
