package com.tung.warehouse.partner.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Nhà cung cấp. Ánh xạ bảng suppliers. */
@Entity
@Table(name = "suppliers")
public class Supplier extends Partner {

    protected Supplier() {
    }

    public Supplier(String code, String name) {
        super(code, name);
    }
}
