package com.ktb10.kgb.member.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** 회원의 V1 푸시 수신 설정을 변경하는 요청입니다. */
public record UpdateMemberSettingsRequest(
        @JsonProperty("push_enabled")
        @NotNull
        Boolean pushEnabled) {
}
