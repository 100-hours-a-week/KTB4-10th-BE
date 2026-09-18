package com.ktb10.kgb.common.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** 오류 응답의 코드, 상세 검증 결과와 요청 추적 ID입니다. */
public record ErrorPayload(
        String code,
        List<ErrorDetail> details,
        @JsonProperty("trace_id") String traceId) {

    public ErrorPayload {
        Objects.requireNonNull(code, "오류 코드는 null일 수 없습니다.");
        details = List.copyOf(Objects.requireNonNull(
                details,
                "오류 상세 목록은 null일 수 없습니다."));
        Objects.requireNonNull(traceId, "추적 ID는 null일 수 없습니다.");
    }
}
