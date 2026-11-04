package com.tung.warehouse.warehouse.entity;

import com.tung.warehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Vị trí lưu trữ cụ thể (kệ A1, tầng 2). Ánh xạ bảng storage_locations. */
@Entity
@Table(name = "storage_locations")
public class StorageLocation extends BaseEntity {

    public static final String RECEIVING_CODE = "RECEIVING";
    public static final String DEFECTIVE_CODE = "DEFECTIVE";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private WarehouseZone zone;

    @Column(nullable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "location_type", nullable = false, length = 20)
    private LocationType locationType = LocationType.NORMAL;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;

    protected StorageLocation() {
    }

    public StorageLocation(WarehouseZone zone, String code, LocationType locationType) {
        this.zone = zone;
        this.code = code;
        this.locationType = locationType;
    }

    public void activate() {
        this.status = WarehouseStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = WarehouseStatus.INACTIVE;
    }

    /** Vị trí hệ thống (RECEIVING, DEFECTIVE) không được xóa, không được ngừng hoạt động. */
    public boolean isSystemLocation() {
        return locationType != LocationType.NORMAL;
    }

    public WarehouseZone getZone() { return zone; }
    public String getCode() { return code; }
    public LocationType getLocationType() { return locationType; }
    public WarehouseStatus getStatus() { return status; }
}
