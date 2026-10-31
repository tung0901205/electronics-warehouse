package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/** Dữ liệu xung đột (trùng mã, sai trạng thái phiếu...): HTTP 409. */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
