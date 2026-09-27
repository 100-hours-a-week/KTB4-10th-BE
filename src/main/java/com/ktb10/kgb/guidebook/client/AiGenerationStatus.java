package com.ktb10.kgb.guidebook.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** AI 서버가 관리하는 생성 작업 상태입니다. */
public enum AiGenerationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    @JsonCreator
    public static AiGenerationStatus from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
