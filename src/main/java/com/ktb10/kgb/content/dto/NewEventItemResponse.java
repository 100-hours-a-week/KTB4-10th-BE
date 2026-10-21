package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.repository.NewEventData;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 신규 행사 목록에 노출할 카드 정보입니다. */
public record NewEventItemResponse(
        @JsonProperty("content_id")
        String contentId,
        String title,
        String address,
        @JsonProperty("thumbnail_url")
        String thumbnailUrl,
        @JsonProperty("start_date")
        LocalDate startDate,
        @JsonProperty("end_date")
        LocalDate endDate,
        @JsonProperty("source_created_at")
        LocalDateTime sourceCreatedAt) {

    public static NewEventItemResponse from(NewEventData data) {
        return new NewEventItemResponse(
                data.sourceContentId(),
                data.title(),
                data.address(),
                data.thumbnailUrl(),
                data.startDate(),
                data.endDate(),
                data.sourceCreatedAt());
    }
}
