package com.ktb10.kgb.common.error;

import java.util.List;
import java.util.Objects;

/** API 오류 응답의 공통 형식입니다. */
public record ErrorResponse(String message, Object data, ErrorPayload error) {

    public ErrorResponse {
        Objects.requireNonNull(message, "오류 메시지는 null일 수 없습니다.");
        Objects.requireNonNull(error, "오류 정보는 null일 수 없습니다.");
        if (data != null) {
            throw new IllegalArgumentException("오류 응답의 데이터는 null이어야 합니다.");
        }
    }

    public static ErrorResponse of(ErrorCode errorCode, List<ErrorDetail> details, String traceId) {
        Objects.requireNonNull(errorCode, "오류 코드는 null일 수 없습니다.");
        return new ErrorResponse(
                errorCode.message(),
                null,
                new ErrorPayload(errorCode.code(), details, traceId));
    }

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return of(errorCode, List.of(), traceId);
    }
}
