package com.ktb10.kgb.common.error;

import java.util.List;
import java.util.Objects;

/** API 오류 응답의 공통 형식입니다. */
public record ErrorResponse(String message, Object data, ErrorPayload error) {

    public ErrorResponse {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(error, "error must not be null");
        if (data != null) {
            throw new IllegalArgumentException("error response data must be null");
        }
    }

    public static ErrorResponse of(ErrorCode errorCode, List<ErrorDetail> details, String traceId) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ErrorResponse(
                errorCode.message(),
                null,
                new ErrorPayload(errorCode.code(), details, traceId));
    }

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return of(errorCode, List.of(), traceId);
    }
}
