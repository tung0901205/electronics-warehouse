package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/** Không tìm thấy dữ liệu: HTTP 404. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String entityName, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entityName + " không tồn tại: " + id);
    }
}
