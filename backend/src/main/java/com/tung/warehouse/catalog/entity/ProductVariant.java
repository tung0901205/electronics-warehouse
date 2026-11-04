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
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Biến thể có SKU (iPhone 15 256GB Đen). Đây là đơn vị mà kho nhập, xuất, đếm. */
@Entity
@Table(name = "product_variants")
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(unique = true, length = 50)
    private String barcode;

    @Column(length = 255)
    private String attributes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tracking_type", nullable = false, length = 20)
    private TrackingType trackingType;

    /** Tiền luôn dùng BigDecimal (khớp DECIMAL(18,2)), không dùng double vì sai số làm tròn. */
    @Column(name = "list_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    protected ProductVariant() {
    }

    public ProductVariant(Product product, String sku, String barcode, String attributes, Unit unit,
                          TrackingType trackingType, BigDecimal listPrice, int warrantyMonths) {
        this.product = product;
        this.sku = sku;
        this.barcode = barcode;
        this.attributes = attributes;
        this.unit = unit;
        this.trackingType = trackingType;
        this.listPrice = listPrice;
        this.warrantyMonths = warrantyMonths;
    }

    /** Sửa thông tin. SKU và kiểu theo dõi (trackingType) không đổi được nên không có ở đây. */
    public void updateDetails(String barcode, String attributes, Unit unit, BigDecimal listPrice, int warrantyMonths) {
        this.barcode = barcode;
        this.attributes = attributes;
        this.unit = unit;
        this.listPrice = listPrice;
        this.warrantyMonths = warrantyMonths;
    }

    public void activate() {
        this.status = CatalogStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = CatalogStatus.INACTIVE;
    }

    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public String getAttributes() { return attributes; }
    public Unit getUnit() { return unit; }
    public TrackingType getTrackingType() { return trackingType; }
    public BigDecimal getListPrice() { return listPrice; }
    public int getWarrantyMonths() { return warrantyMonths; }
    public CatalogStatus getStatus() { return status; }
}
