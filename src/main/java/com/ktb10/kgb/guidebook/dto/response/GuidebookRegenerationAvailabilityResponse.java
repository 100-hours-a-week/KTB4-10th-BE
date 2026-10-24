package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.service.GuidebookRegenerationPolicy.RegenerationAvailability;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 가이드북 완료 화면에서 사용하는 재생성 가능 상태입니다. */
public record GuidebookRegenerationAvailabilityResponse(
        @JsonProperty("remaining_count")
        int remainingCount,
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
        boolean available) {

    public static GuidebookRegenerationAvailabilityResponse from(
            RegenerationAvailability availability) {
        OffsetDateTime expiresAt = availability.expiresAt() == null
                ? null
                : availability.expiresAt().atOffset(ZoneOffset.UTC);
        return new GuidebookRegenerationAvailabilityResponse(
                availability.remainingCount(),
                expiresAt,
                availability.available());
    }
}
