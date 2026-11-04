package com.tung.warehouse.warehouse.service;

import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.warehouse.dto.ZoneRequest;
import com.tung.warehouse.warehouse.dto.ZoneResponse;
import com.tung.warehouse.warehouse.entity.Warehouse;
import com.tung.warehouse.warehouse.entity.WarehouseZone;
import com.tung.warehouse.warehouse.repository.StorageLocationRepository;
import com.tung.warehouse.warehouse.repository.WarehouseRepository;
import com.tung.warehouse.warehouse.repository.WarehouseZoneRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.4 (phần khu vực). */
@Service
public class ZoneService {

    private final WarehouseZoneRepository zoneRepository;
    private final WarehouseRepository warehouseRepository;
    private final StorageLocationRepository locationRepository;
    private final WarehouseAccessService accessService;

    public ZoneService(WarehouseZoneRepository zoneRepository, WarehouseRepository warehouseRepository,
                       StorageLocationRepository locationRepository, WarehouseAccessService accessService) {
        this.zoneRepository = zoneRepository;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> findByWarehouse(Long warehouseId) {
        accessService.assertCanAccess(warehouseId);
        return zoneRepository.findByWarehouseIdOrderByCodeAsc(warehouseId).stream().map(ZoneResponse::from).toList();
    }

    @Transactional
    public ZoneResponse create(Long warehouseId, ZoneRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Kho", warehouseId));
        String code = request.code().trim().toUpperCase();
        if (WarehouseZone.SYSTEM_CODE.equals(code)) {
            throw new ConflictException("Mã " + code + " dành riêng cho khu hệ thống");
        }
        if (zoneRepository.existsByWarehouseIdAndCode(warehouseId, code)) {
            throw new ConflictException("Mã khu vực đã tồn tại trong kho này: " + code);
        }
        return ZoneResponse.from(zoneRepository.save(new WarehouseZone(warehouse, code, request.name().trim())));
    }

    /** Chỉ sửa tên; mã khu vực không đổi được. */
    @Transactional
    public ZoneResponse rename(Long zoneId, ZoneRequest request) {
        WarehouseZone zone = findOrThrow(zoneId);
        if (zone.isSystemZone()) {
            throw new ConflictException("Không thể sửa khu hệ thống");
        }
        zone.rename(request.name().trim());
        return ZoneResponse.from(zone);
    }

    @Transactional
    public void delete(Long zoneId) {
        WarehouseZone zone = findOrThrow(zoneId);
        if (zone.isSystemZone()) {
            throw new ConflictException("Không thể xóa khu hệ thống");
        }
        if (locationRepository.existsByZoneId(zoneId)) {
            throw new ConflictException("Không thể xóa: khu vực còn vị trí lưu trữ");
        }
        zoneRepository.delete(zone);
    }

    private WarehouseZone findOrThrow(Long zoneId) {
        return zoneRepository.findById(zoneId).orElseThrow(() -> new NotFoundException("Khu vực", zoneId));
    }
}
