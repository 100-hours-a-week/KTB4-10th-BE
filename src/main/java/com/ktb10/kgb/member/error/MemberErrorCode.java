package com.ktb10.kgb.member.error;

import com.ktb10.kgb.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 회원과 취향 처리 과정에서 발생하는 업무 오류 코드입니다. */
public enum MemberErrorCode implements ErrorCode {
    PREFERENCE_INVALID(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "회원의 기본 취향 정보가 올바르지 않습니다.");

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
