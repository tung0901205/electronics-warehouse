package com.tung.warehouse.identity.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** Tài khoản người dùng. Ánh xạ bảng users. */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** Chuỗi băm BCrypt, KHÔNG BAO GIỜ lưu mật khẩu thô. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Quan hệ n-n giữa users và roles, qua bảng trung gian user_roles.
     * LAZY: chỉ nạp danh sách vai trò khi thật sự dùng tới.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /**
     * [MỚI Ngày 7] Các kho mà người dùng được làm việc, lưu ở bảng user_warehouse_access (user_id, warehouse_id).
     * Chỉ giữ id kho (không giữ entity Warehouse) để module identity không phụ thuộc vào module warehouse.
     * @ElementCollection: một tập giá trị đơn giản (số) nằm ở bảng riêng, không phải entity.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_warehouse_access", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "warehouse_id")
    private Set<Long> warehouseIds = new HashSet<>();

    protected User() {
    }

    public User(String username, String email, String passwordHash, String fullName) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    /** [MỚI Ngày 5] Thay toàn bộ vai trò bằng danh sách mới (UC1.5). */
    public void replaceRoles(Set<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    /** [MỚI Ngày 7] Thay toàn bộ danh sách kho được gán. */
    public void replaceWarehouseIds(Set<Long> newIds) {
        this.warehouseIds.clear();
        this.warehouseIds.addAll(newIds);
    }

    /** [MỚI Ngày 5] Sửa thông tin cá nhân. Username không đổi được. */
    public void updateProfile(String email, String fullName, String phone) {
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
    }

    /** Đổi mật khẩu: chỉ nhận chuỗi ĐÃ BĂM. Việc băm do service làm. */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<Role> getRoles() { return roles; }
    public Set<Long> getWarehouseIds() { return warehouseIds; }
}
