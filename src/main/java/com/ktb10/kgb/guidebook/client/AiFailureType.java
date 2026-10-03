package com.ktb10.kgb.guidebook.client;

/** AI 서버 연동 실패 원인과 시스템 재시도 가능 여부를 표현합니다. */
public enum AiFailureType {

    CONNECT_TIMEOUT("connect_timeout", true),
    READ_TIMEOUT("read_timeout", true),
    UPSTREAM_4XX("upstream_4xx", false),
    UPSTREAM_5XX("upstream_5xx", true),
    CONNECTION_ERROR("connection_error", true),
    INVALID_RESPONSE("invalid_response", false),
    UNEXPECTED_ERROR("unexpected_error", false);

    private final String code;
    private final boolean retryable;

    AiFailureType(String code, boolean retryable) {
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
