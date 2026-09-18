package com.ktb10.kgb.common.error;

import java.util.Objects;

/** 요청 검증 오류가 발생한 필드와 안정적인 사유 코드입니다. */
public record ErrorDetail(String field, String reason) {

    public ErrorDetail {
        Objects.requireNonNull(field, "오류 필드는 null일 수 없습니다.");
        Objects.requireNonNull(reason, "오류 사유는 null일 수 없습니다.");
    }
}
