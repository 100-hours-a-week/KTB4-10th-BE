package com.ktb10.kgb.guidebook.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** AI 서버에 전달하는 가이드북 재생성 요청입니다. */
public record AiGuidebookRegenerationRequest(
        @JsonProperty("request_id")
        String requestId,
        String feedback) {
}
