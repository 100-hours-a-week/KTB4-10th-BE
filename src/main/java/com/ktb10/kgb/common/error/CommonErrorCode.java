package com.ktb10.kgb.common.error;

import org.springframework.http.HttpStatus;

/** 여러 도메인에서 공통으로 사용하는 오류 코드입니다. */
public enum CommonErrorCode implements ErrorCode {
    COMMON_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "invalid_request"),
    AUTH_SESSION_REQUIRED(HttpStatus.UNAUTHORIZED, "authentication_required"),
    AUTH_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "session_expired"),
    RESOURCE_FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "resource_not_found"),
    RESOURCE_STATE_CONFLICT(HttpStatus.CONFLICT, "resource_state_conflict"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error"),
    UPSTREAM_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "upstream_service_error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable");

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
