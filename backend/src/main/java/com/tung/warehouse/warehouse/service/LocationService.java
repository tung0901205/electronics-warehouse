package com.tung.warehouse.warehouse.service;

import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.warehouse.dto.LocationRequest;
import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.entity.LocationType;
import com.tung.warehouse.warehouse.entity.StorageLocation;
import com.tung.warehouse.warehouse.entity.WarehouseZone;
import com.tung.warehouse.warehouse.repository.StorageLocationRepository;
import com.tung.warehouse.warehouse.repository.WarehouseZoneRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.4 (phần vị trí lưu trữ). */
@Service
public class LocationService {

    private final StorageLocationRepository locationRepository;
    private final WarehouseZoneRepository zoneRepository;
    private final WarehouseAccessService accessService;

    public LocationService(StorageLocationRepository locationRepository, WarehouseZoneRepository zoneRepository,
                           WarehouseAccessService accessService) {
        this.locationRepository = locationRepository;
        this.zoneRepository = zoneRepository;
        this.accessService = accessService;
    }

    /** Tất cả vị trí của một kho (dùng cho ô chọn vị trí ở phiếu nhập). */
    @Transactional(readOnly = true)
    public List<LocationResponse> findByWarehouse(Long warehouseId) {
        accessService.assertCanAccess(warehouseId);
        return locationRepository.findByZoneWarehouseIdOrderByCodeAsc(warehouseId).stream()
                .map(LocationResponse::from).toList();
    }

    /** [MỚI Giai đoạn 3] Cho module khác (inventory) hỏi thông tin một vị trí: thuộc kho nào, loại gì, còn hoạt động không. */
    @Transactional(readOnly = true)
    public LocationResponse getById(Long id) {
        return LocationResponse.from(findOrThrow(id));
    }

    /** [MỚI Giai đoạn 6] Vị trí hàng lỗi (DEFECTIVE) của một kho. Kho nào cũng có sẵn một vị trí như vậy. */
    @Transactional(readOnly = true)
    public LocationResponse findDefectiveLocation(Long warehouseId) {
        return LocationResponse.from(locationRepository
                .findFirstByZoneWarehouseIdAndLocationType(warehouseId, LocationType.DEFECTIVE)
                .orElseThrow(() -> new NotFoundException("Vị trí hàng lỗi của kho", warehouseId)));
    }

    @Transactional
    public LocationResponse create(Long zoneId, LocationRequest request) {
        WarehouseZone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException("Khu vực", zoneId));
        String code = request.code().trim().toUpperCase();

        if (zone.isSystemZone()) {
            throw new ConflictException("Không thêm vị trí vào khu hệ thống");
        }
        if (code.equals(StorageLocation.RECEIVING_CODE) || code.equals(StorageLocation.DEFECTIVE_CODE)) {
            throw new ConflictException("Mã " + code + " dành riêng cho vị trí hệ thống");
        }
        if (locationRepository.existsByZoneIdAndCode(zoneId, code)) {
            throw new ConflictException("Mã vị trí đã tồn tại trong khu vực này: " + code);
        }
        return LocationResponse.from(locationRepository.save(new StorageLocation(zone, code, LocationType.NORMAL)));
    }

    @Transactional
    public LocationResponse setActive(Long id, boolean active) {
        StorageLocation location = findOrThrow(id);
        if (location.isSystemLocation()) {
            throw new ConflictException("Không thể thay đổi trạng thái vị trí hệ thống");
        }
        if (active) {
            location.activate();
        } else {
            location.deactivate();
        }
        return LocationResponse.from(location);
    }

    /**
     * Chỉ xóa được vị trí thường. Khi đã có tồn kho tham chiếu tới vị trí này (Giai đoạn 3),
     * khóa ngoại của database sẽ chặn việc xóa và trả lỗi 409.
     */
    @Transactional
    public void delete(Long id) {
        StorageLocation location = findOrThrow(id);
        if (location.isSystemLocation()) {
            throw new ConflictException("Không thể xóa vị trí hệ thống");
        }
        locationRepository.delete(location);
    }

    private StorageLocation findOrThrow(Long id) {
        return locationRepository.findById(id).orElseThrow(() -> new NotFoundException("Vị trí", id));
    }
}
