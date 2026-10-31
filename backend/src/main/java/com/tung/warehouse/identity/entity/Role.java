package com.tung.warehouse.identity.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Vai trò: ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, SALES, VIEWER. Ánh xạ bảng roles. */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /** JPA bắt buộc có constructor không tham số (để protected cho code khác không gọi bừa). */
    protected Role() {
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
}
