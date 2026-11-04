package com.tung.warehouse.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Nơi duy nhất đổi exception thành JSON lỗi. Dùng ProblemDetail (chuẩn RFC 9457) của Spring.
 * Kế thừa ResponseEntityExceptionHandler để Spring tự xử lý các lỗi web thông thường
 * (sai method, thiếu tham số, không tìm thấy đường dẫn...) theo cùng định dạng.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Lỗi nghiệp vụ do mình ném ra. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getCode());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    /**
     * Database từ chối vì vi phạm ràng buộc (UNIQUE, khóa ngoại...). Thường gặp khi hai người cùng lúc
     * tạo một mã: cả hai cùng qua bước kiểm tra "đã tồn tại chưa", nhưng chỉ một người ghi được.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Dữ liệu vi phạm ràng buộc: bị trùng hoặc đang được sử dụng ở nơi khác");
        problem.setTitle("DATA_INTEGRITY_VIOLATION");
        problem.setProperty("code", "DATA_INTEGRITY_VIOLATION");
        return problem;
    }

    /** Hai người cùng sửa một bản ghi: @Version phát hiện người sau đang sửa trên dữ liệu cũ. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Dữ liệu vừa được người khác thay đổi, vui lòng tải lại rồi thử lại");
        problem.setTitle("CONCURRENT_UPDATE");
        problem.setProperty("code", "CONCURRENT_UPDATE");
        return problem;
    }

    /** Lỗi validate (@Valid) ở request body: gom lỗi theo từng trường. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");
        problem.setTitle("VALIDATION_ERROR");
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
