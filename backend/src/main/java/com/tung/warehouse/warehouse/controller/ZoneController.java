package com.tung.warehouse.warehouse.controller;

import com.tung.warehouse.warehouse.dto.LocationRequest;
import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.dto.ZoneRequest;
import com.tung.warehouse.warehouse.dto.ZoneResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import com.tung.warehouse.warehouse.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sửa, xóa khu vực và thêm vị trí vào khu vực: chỉ Admin (UC2.4). */
@RestController
@RequestMapping("/api/zones")
@Tag(name = "Khu vực")
@PreAuthorize("hasRole('ADMIN')")
public class ZoneController {

    private final ZoneService zoneService;
    private final LocationService locationService;

    public ZoneController(ZoneService zoneService, LocationService locationService) {
        this.zoneService = zoneService;
        this.locationService = locationService;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Đổi tên khu vực (không đổi được mã)")
    public ZoneResponse rename(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        return zoneService.rename(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa khu vực (chỉ khi không còn vị trí; không xóa được khu hệ thống)")
    public void delete(@PathVariable Long id) {
        zoneService.delete(id);
    }

    @PostMapping("/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm vị trí (kệ, tầng) vào khu vực")
    public LocationResponse createLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return locationService.create(id, request);
    }
}
