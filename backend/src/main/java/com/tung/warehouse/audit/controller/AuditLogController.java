package com.tung.warehouse.audit.controller;

import com.tung.warehouse.audit.dto.AuditLogResponse;
import com.tung.warehouse.audit.service.AuditService;
import com.tung.warehouse.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Xem nhật ký thao tác: chỉ Admin (UC11.2). */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Nhật ký thao tác")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Tìm nhật ký thao tác (lọc theo hành động, loại đối tượng, người thực hiện; có phân trang)")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return auditService.search(action, entityType, userId, page, size);
    }
}
