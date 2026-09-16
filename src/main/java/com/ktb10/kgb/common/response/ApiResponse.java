package com.ktb10.kgb.common.response;

import java.util.Objects;

/** API 성공 응답의 공통 형식입니다. */
public record ApiResponse<T>(String message, T data) {

    public ApiResponse {
        Objects.requireNonNull(message, "message must not be null");
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, data);
    }
}
