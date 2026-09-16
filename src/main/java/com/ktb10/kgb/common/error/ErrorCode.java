package com.ktb10.kgb.common.error;

import org.springframework.http.HttpStatus;

/** 도메인 오류 코드가 공통 예외 처리에 제공해야 하는 계약입니다. */
public interface ErrorCode {

    HttpStatus httpStatus();

    String code();

    String message();
}
