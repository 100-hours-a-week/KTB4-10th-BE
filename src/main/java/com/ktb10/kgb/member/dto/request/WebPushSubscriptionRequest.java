package com.ktb10.kgb.member.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 현재 브라우저의 표준 Web Push 구독 등록·갱신 요청입니다. */
public record WebPushSubscriptionRequest(
        @NotBlank
        @Size(max = 4096)
        String endpoint,
        @JsonProperty("expiration_time")
        @Positive
        Long expirationTime,
        @NotNull
        @Valid
        WebPushSubscriptionKeysRequest keys) {
}
