package com.ktb10.kgb.credit.error;

import com.ktb10.kgb.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 생성권 지갑과 관리자 쿠폰 처리 오류를 제공합니다. */
public enum CreditErrorCode implements ErrorCode {
    COUPON_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "유효하지 않은 쿠폰입니다."),
    COUPON_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "쿠폰 등록이 잠시 중단되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    CreditErrorCode(HttpStatus httpStatus, String message) {
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
