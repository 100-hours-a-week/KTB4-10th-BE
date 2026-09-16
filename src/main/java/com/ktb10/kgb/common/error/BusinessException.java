package com.ktb10.kgb.common.error;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/** 예상 가능한 업무 실패를 공통 오류 코드와 함께 전달합니다. */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final List<ErrorDetail> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of(), null);
    }

    public BusinessException(ErrorCode errorCode, List<ErrorDetail> details) {
        this(errorCode, details, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, List.of(), cause);
    }

    private BusinessException(
            ErrorCode errorCode,
            List<ErrorDetail> details,
            Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").code(), cause);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
