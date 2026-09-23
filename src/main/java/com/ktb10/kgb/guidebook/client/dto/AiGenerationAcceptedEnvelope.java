package com.ktb10.kgb.guidebook.client.dto;

import java.util.Objects;

/** AI 서버의 공통 응답 형식으로 감싼 생성 작업 접수 응답입니다. */
public record AiGenerationAcceptedEnvelope(
        String message,
        AiGenerationAcceptedResponse data) {

    public AiGenerationAcceptedEnvelope {
        Objects.requireNonNull(message, "AI 응답 메시지는 null일 수 없습니다.");
        Objects.requireNonNull(data, "AI 응답 데이터는 null일 수 없습니다.");
    }
}
