package com.tung.warehouse.common.util;

import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * Thử lại khi database báo deadlock hoặc hết thời gian chờ khóa.
 * Khi nhiều người cùng khóa nhiều dòng tồn, MySQL đôi khi hủy một transaction để gỡ deadlock;
 * việc cần làm là chạy lại từ đầu. Hàm này phải bao BÊN NGOÀI transaction (gọi từ controller),
 * vì transaction đã bị hủy thì không thể chạy tiếp mà phải bắt đầu transaction mới.
 */
public final class Retry {

    private Retry() {
    }

    public static <T> T onDeadlock(int maxAttempts, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (PessimisticLockingFailureException e) { // gồm cả DeadlockLoserDataAccessException và CannotAcquireLockException
                if (attempt >= maxAttempts) {
                    throw e;
                }
            }
        }
    }
}
