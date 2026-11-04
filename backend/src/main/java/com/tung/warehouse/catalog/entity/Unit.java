package com.tung.warehouse.catalog.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Đơn vị tính (Cái, Bộ, Hộp...). Ánh xạ bảng units. */
@Entity
@Table(name = "units")
public class Unit extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    protected Unit() {
    }

    public Unit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String name) {
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
