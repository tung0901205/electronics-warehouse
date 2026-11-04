package com.tung.warehouse.warehouse.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Khu vực trong kho (Khu A, Khu B...). Ánh xạ bảng warehouse_zones. */
@Entity
@Table(name = "warehouse_zones")
public class WarehouseZone extends BaseEntity {

    /** Mã của khu hệ thống: khu đặc biệt, tự tạo cùng kho, chứa vị trí RECEIVING và DEFECTIVE. */
    public static final String SYSTEM_CODE = "SYS";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    protected WarehouseZone() {
    }

    public WarehouseZone(Warehouse warehouse, String code, String name) {
        this.warehouse = warehouse;
        this.code = code;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean isSystemZone() {
        return SYSTEM_CODE.equals(code);
    }

    public Warehouse getWarehouse() { return warehouse; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
