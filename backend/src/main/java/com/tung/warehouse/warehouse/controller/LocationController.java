package com.tung.warehouse.warehouse.controller;

import com.tung.warehouse.warehouse.dto.LocationResponse;
import com.tung.warehouse.warehouse.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đổi trạng thái, xóa vị trí: chỉ Admin (UC2.4). */
@RestController
@RequestMapping("/api/locations")
@Tag(name = "Vị trí lưu trữ")
@PreAuthorize("hasRole('ADMIN')")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt vị trí")
    public LocationResponse activate(@PathVariable Long id) {
        return locationService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Ngừng hoạt động vị trí")
    public LocationResponse deactivate(@PathVariable Long id) {
        return locationService.setActive(id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa vị trí thường (không xóa được RECEIVING, DEFECTIVE)")
    public void delete(@PathVariable Long id) {
        locationService.delete(id);
    }
}
