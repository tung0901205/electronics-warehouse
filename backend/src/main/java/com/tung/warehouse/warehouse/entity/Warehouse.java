package com.tung.warehouse.warehouse.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Kho hàng. Ánh xạ bảng warehouses. */
@Entity
@Table(name = "warehouses")
public class Warehouse extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;

    protected Warehouse() {
    }

    public Warehouse(String code, String name, String address) {
        this.code = code;
        this.name = name;
        this.address = address;
    }

    /** Sửa thông tin. Mã kho không đổi được. */
    public void updateInfo(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public void activate() {
        this.status = WarehouseStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = WarehouseStatus.INACTIVE;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public WarehouseStatus getStatus() { return status; }
}
