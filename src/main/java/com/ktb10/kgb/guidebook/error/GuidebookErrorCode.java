package com.ktb10.kgb.guidebook.error;

import com.ktb10.kgb.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 가이드북 생성과 조회 과정에서 발생하는 업무 오류 코드입니다. */
public enum GuidebookErrorCode implements ErrorCode {
    IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 멱등 키가 다른 생성 요청에 사용되었습니다."),
    GENERATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "이미 진행 중인 가이드북 생성 작업이 있습니다."),
    CREDIT_INSUFFICIENT(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "가이드북 생성권이 부족합니다."),
    GUIDEBOOK_INVALID_PERIOD(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "여행 기간이 올바르지 않습니다."),
    GUIDEBOOK_INVALID_PARTY(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "동행 유형과 인원수가 올바르지 않습니다."),
    GUIDEBOOK_INVALID_REGION(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "시도와 시군구 정보가 올바르지 않습니다."),
    PREFERENCE_INVALID(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "회원의 기본 취향 정보가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    GuidebookErrorCode(HttpStatus httpStatus, String message) {
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
