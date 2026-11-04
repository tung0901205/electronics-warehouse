package com.tung.warehouse.partner.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Khách hàng. Ánh xạ bảng customers. */
@Entity
@Table(name = "customers")
public class Customer extends Partner {

    protected Customer() {
    }

    public Customer(String code, String name) {
        super(code, name);
    }
}
