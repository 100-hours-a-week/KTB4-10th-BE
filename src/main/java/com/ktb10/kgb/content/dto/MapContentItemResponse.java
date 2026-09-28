package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** 지도에 표시할 관광 콘텐츠 한 건입니다. */
public record MapContentItemResponse(
        @JsonProperty("content_id")
        String contentId,
        String title,
        @JsonProperty("content_type")
        ContentType contentType,
        String address,
        double latitude,
        double longitude,
        @JsonProperty("thumbnail_url")
        String thumbnailUrl,
        @JsonProperty("event_period")
        EventPeriod eventPeriod,
        @JsonProperty("is_favorite")
        boolean favorite) {

    public enum ContentType {
        PLACE,
        EVENT
    }

    public record EventPeriod(
            @JsonProperty("start_date")
            LocalDate startDate,
            @JsonProperty("end_date")
            LocalDate endDate) {
    }
}
