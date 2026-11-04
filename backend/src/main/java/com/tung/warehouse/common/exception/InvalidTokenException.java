package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/** Refresh token không tồn tại, hết hạn hoặc đã bị thu hồi: HTTP 401. */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", message);
    }
}
