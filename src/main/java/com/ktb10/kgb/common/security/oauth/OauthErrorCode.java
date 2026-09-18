package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** OAuth 브라우저 복귀 흐름에서 노출할 수 있는 제한된 실패 코드입니다. */
public enum OauthErrorCode implements ErrorCode {
    OAUTH_ACCESS_DENIED(HttpStatus.BAD_REQUEST, "카카오 로그인이 취소되었습니다."),
    OAUTH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "로그인 요청이 만료되었거나 올바르지 않습니다."),
    OAUTH_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "카카오 인증을 완료하지 못했습니다."),
    OAUTH_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 로그인을 일시적으로 이용할 수 없습니다."),
    OAUTH_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "로그인 처리 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    OauthErrorCode(HttpStatus httpStatus, String message) {
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
