package com.tung.warehouse.catalog.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Danh mục sản phẩm, có thể lồng nhau (Điện thoại → iPhone). Ánh xạ bảng categories. */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Quan hệ tự tham chiếu: một danh mục trỏ tới danh mục cha của nó (cùng bảng).
     * parent = null nghĩa là danh mục gốc.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    protected Category() {
    }

    public Category(String name, Category parent) {
        this.name = name;
        this.parent = parent;
    }

    public void update(String name, Category parent) {
        this.name = name;
        this.parent = parent;
    }

    public void activate() {
        this.status = CatalogStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = CatalogStatus.INACTIVE;
    }

    public String getName() { return name; }
    public Category getParent() { return parent; }
    public CatalogStatus getStatus() { return status; }
}
