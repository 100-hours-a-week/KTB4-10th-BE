package com.ktb10.kgb.member.error;

import com.ktb10.kgb.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 회원과 취향 처리 과정에서 발생하는 업무 오류 코드입니다. */
public enum MemberErrorCode implements ErrorCode {
    PREFERENCE_INVALID(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "회원의 기본 취향 정보가 올바르지 않습니다."),
    WEB_PUSH_SUBSCRIPTION_INVALID(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Web Push 구독 정보가 올바르지 않습니다."),
    WEB_PUSH_SUBSCRIPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Web Push 구독을 찾을 수 없습니다."),
    WEB_PUSH_SUBSCRIPTION_CONFLICT(
            HttpStatus.CONFLICT,
            "다른 회원이 사용 중인 Web Push 구독입니다."),
    WEB_PUSH_CONFIGURATION_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Web Push 설정을 일시적으로 사용할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    MemberErrorCode(HttpStatus httpStatus, String message) {
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
