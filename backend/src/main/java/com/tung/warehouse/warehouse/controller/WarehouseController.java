package com.tung.warehouse.warehouse.controller;

import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.dto.WarehouseRequest;
import com.tung.warehouse.warehouse.dto.WarehouseResponse;
import com.tung.warehouse.warehouse.dto.ZoneRequest;
import com.tung.warehouse.warehouse.dto.ZoneResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import com.tung.warehouse.warehouse.service.WarehouseService;
import com.tung.warehouse.warehouse.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xem (GET): mọi người đã đăng nhập, nhưng chỉ thấy kho được gán (Admin thấy tất cả).
 * Thêm, sửa, đổi trạng thái: chỉ Admin (UC2.4).
 */
@RestController
@RequestMapping("/api/warehouses")
@Tag(name = "Kho")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final ZoneService zoneService;
    private final LocationService locationService;

    public WarehouseController(WarehouseService warehouseService, ZoneService zoneService, LocationService locationService) {
        this.warehouseService = warehouseService;
        this.zoneService = zoneService;
        this.locationService = locationService;
    }

    @GetMapping
    @Operation(summary = "Danh sách kho mà người dùng được phép làm việc")
    public List<WarehouseResponse> findVisible() {
        return warehouseService.findVisible();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem một kho")
    public WarehouseResponse getById(@PathVariable Long id) {
        return warehouseService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm kho (tự tạo khu hệ thống kèm vị trí RECEIVING và DEFECTIVE)")
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sửa tên, địa chỉ kho (không đổi được mã)")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Kích hoạt kho")
    public WarehouseResponse activate(@PathVariable Long id) {
        return warehouseService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ngừng hoạt động kho")
    public WarehouseResponse deactivate(@PathVariable Long id) {
        return warehouseService.setActive(id, false);
    }

    // ---------- Khu vực và vị trí của một kho ----------

    @GetMapping("/{id}/zones")
    @Operation(summary = "Danh sách khu vực của kho")
    public List<ZoneResponse> zones(@PathVariable Long id) {
        return zoneService.findByWarehouse(id);
    }

    @PostMapping("/{id}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm khu vực vào kho")
    public ZoneResponse createZone(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        return zoneService.create(id, request);
    }

    @GetMapping("/{id}/locations")
    @Operation(summary = "Tất cả vị trí lưu trữ của kho (dùng cho ô chọn vị trí ở phiếu nhập)")
    public List<LocationResponse> locations(@PathVariable Long id) {
        return locationService.findByWarehouse(id);
    }
}
