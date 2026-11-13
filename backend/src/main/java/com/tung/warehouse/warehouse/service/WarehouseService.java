package com.tung.warehouse.warehouse.service;

import com.tung.warehouse.common.exception.ConflictException;
import com.tung.warehouse.common.exception.NotFoundException;
import com.tung.warehouse.common.security.CurrentUser;
import com.tung.warehouse.warehouse.dto.WarehouseRequest;
import com.tung.warehouse.warehouse.dto.WarehouseResponse;
import com.tung.warehouse.warehouse.entity.LocationType;
import com.tung.warehouse.warehouse.entity.StorageLocation;
import com.tung.warehouse.warehouse.entity.Warehouse;
import com.tung.warehouse.warehouse.entity.WarehouseZone;
import com.tung.warehouse.warehouse.repository.StorageLocationRepository;
import com.tung.warehouse.warehouse.repository.WarehouseRepository;
import com.tung.warehouse.warehouse.repository.WarehouseZoneRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ UC2.4 (phần kho) và UC2.5 (tra cứu). */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseZoneRepository zoneRepository;
    private final StorageLocationRepository locationRepository;
    private final WarehouseAccessService accessService;

    public WarehouseService(WarehouseRepository warehouseRepository, WarehouseZoneRepository zoneRepository,
                            StorageLocationRepository locationRepository, WarehouseAccessService accessService) {
        this.warehouseRepository = warehouseRepository;
        this.zoneRepository = zoneRepository;
        this.locationRepository = locationRepository;
        this.accessService = accessService;
    }

    /** Admin thấy mọi kho; người khác chỉ thấy các kho được gán. */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> findVisible() {
        List<Warehouse> warehouses;
        if (CurrentUser.isAdmin()) {
            warehouses = warehouseRepository.findAllByOrderByNameAsc();
        } else {
            Set<Long> ids = CurrentUser.warehouseIds();
            warehouses = ids.isEmpty() ? List.of() : warehouseRepository.findByIdInOrderByNameAsc(ids);
        }
        return warehouses.stream().map(WarehouseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(Long id) {
        accessService.assertCanAccess(id);
        return WarehouseResponse.from(findOrThrow(id));
    }

    /**
     * [MỚI Giai đoạn 6] Tra thông tin một kho mà KHÔNG kiểm tra phạm vi kho của người dùng.
     * Dùng khi cần biết kho đích của phiếu điều chuyển (người tạo phiếu có thể không được gán kho đích).
     */
    @Transactional(readOnly = true)
    public WarehouseResponse lookup(Long id) {
        return WarehouseResponse.from(findOrThrow(id));
    }

    /**
     * Tạo kho và tự tạo luôn "khu hệ thống" (SYS) kèm hai vị trí đặc biệt RECEIVING và DEFECTIVE.
     * Cả bốn thao tác ghi nằm trong một transaction: lỗi ở bước nào thì không có gì được lưu.
     */
    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        String code = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCode(code)) {
            throw new ConflictException("Mã kho đã tồn tại: " + code);
        }
        Warehouse warehouse = warehouseRepository.save(new Warehouse(code, request.name().trim(), request.address()));

        WarehouseZone systemZone = zoneRepository.save(
                new WarehouseZone(warehouse, WarehouseZone.SYSTEM_CODE, "Khu hệ thống"));
        locationRepository.save(new StorageLocation(systemZone, StorageLocation.RECEIVING_CODE, LocationType.RECEIVING));
        locationRepository.save(new StorageLocation(systemZone, StorageLocation.DEFECTIVE_CODE, LocationType.DEFECTIVE));

        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = findOrThrow(id);
        warehouse.updateInfo(request.name().trim(), request.address());
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public WarehouseResponse setActive(Long id, boolean active) {
        Warehouse warehouse = findOrThrow(id);
        if (active) {
            warehouse.activate();
        } else {
            warehouse.deactivate();
        }
        return WarehouseResponse.from(warehouse);
    }

    /**
     * Dành cho module khác (identity) gọi khi gán kho cho người dùng:
     * mọi id phải là kho có thật, nếu không báo 404.
     */
    @Transactional(readOnly = true)
    public void checkAllExist(Collection<Long> warehouseIds) {
        for (Long id : warehouseIds) {
            if (!warehouseRepository.existsById(id)) {
                throw new NotFoundException("Kho", id);
            }
        }
    }

    private Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id).orElseThrow(() -> new NotFoundException("Kho", id));
    }
}
