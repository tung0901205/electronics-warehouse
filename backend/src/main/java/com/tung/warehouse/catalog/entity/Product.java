package com.tung.warehouse.catalog.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Sản phẩm cha (iPhone 15). SKU nằm ở biến thể (ProductVariant), không nằm ở đây. */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    // Cột MySQL kiểu TEXT; hai chú thích dưới giúp Hibernate hiểu đúng kiểu này khi kiểm tra (validate)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    /**
     * Quan hệ 1-n: một sản phẩm có nhiều biến thể.
     * mappedBy = "product": phía ProductVariant giữ khóa ngoại product_id, phía này chỉ là "chiều ngược lại".
     */
    @OneToMany(mappedBy = "product")
    @OrderBy("id ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
    }

    public Product(String name, Category category, Brand brand, String description) {
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.description = description;
    }

    public void update(String name, Category category, Brand brand, String description) {
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.description = description;
    }

    public void activate() {
        this.status = CatalogStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = CatalogStatus.INACTIVE;
    }

    public String getName() { return name; }
    public Category getCategory() { return category; }
    public Brand getBrand() { return brand; }
    public String getDescription() { return description; }
    public CatalogStatus getStatus() { return status; }
    public List<ProductVariant> getVariants() { return variants; }
}
