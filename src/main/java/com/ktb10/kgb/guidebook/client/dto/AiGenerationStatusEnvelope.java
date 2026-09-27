package com.ktb10.kgb.guidebook.client.dto;

import java.util.Objects;

/** AI 상태 조회 API의 공통 응답 래퍼입니다. */
public record AiGenerationStatusEnvelope(
        String message,
        AiGenerationStatusResponse data) {

    public AiGenerationStatusEnvelope {
        Objects.requireNonNull(message);
        Objects.requireNonNull(data);
    }
}
