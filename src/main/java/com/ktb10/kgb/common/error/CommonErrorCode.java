package com.ktb10.kgb.common.error;

import org.springframework.http.HttpStatus;

/** 여러 도메인에서 공통으로 사용하는 오류 코드입니다. */
public enum CommonErrorCode implements ErrorCode {
    COMMON_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    AUTH_SESSION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인 세션이 만료되었습니다."),
    RESOURCE_FORBIDDEN(HttpStatus.FORBIDDEN, "요청한 작업을 수행할 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    RESOURCE_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 요청을 처리할 수 없습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    UPSTREAM_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 처리 중 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 이용할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
