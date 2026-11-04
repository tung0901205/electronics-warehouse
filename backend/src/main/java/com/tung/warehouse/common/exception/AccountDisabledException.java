package com.tung.warehouse.common.exception;

import org.springframework.http.HttpStatus;

/** Tài khoản bị vô hiệu hóa: HTTP 403. */
public class AccountDisabledException extends BusinessException {

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa");
    }
}
