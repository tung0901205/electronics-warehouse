package com.tung.warehouse.partner.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Các cột chung của nhà cung cấp và khách hàng (hai bảng giống hệt nhau).
 * @MappedSuperclass: lớp này không có bảng riêng, cột của nó được dán vào bảng suppliers và customers.
 */
@MappedSuperclass
public abstract class Partner extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    /**
     * Lưu chữ "ACTIVE"/"INACTIVE" vào cột VARCHAR.
     * @JdbcTypeCode(VARCHAR): Hibernate 6.2 trở lên có thể tự chọn kiểu ENUM riêng của MySQL cho enum,
     * khai báo rõ VARCHAR để khớp cột trong migration và để validate không báo lỗi.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PartnerStatus status = PartnerStatus.ACTIVE;

    protected Partner() {
    }

    protected Partner(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** Sửa thông tin. Mã (code) không đổi được sau khi tạo. */
    public void updateInfo(String name, String contactName, String phone, String email, String address, String taxCode) {
        this.name = name;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.taxCode = taxCode;
    }

    public void activate() {
        this.status = PartnerStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = PartnerStatus.INACTIVE;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getContactName() { return contactName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getTaxCode() { return taxCode; }
    public PartnerStatus getStatus() { return status; }
}
