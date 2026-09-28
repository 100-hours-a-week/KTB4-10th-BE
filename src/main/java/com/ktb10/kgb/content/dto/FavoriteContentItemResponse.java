package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import java.time.OffsetDateTime;

/** 회원이 저장한 관심 장소 한 건입니다. */
public record FavoriteContentItemResponse(
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
        @JsonProperty("favorited_at")
        OffsetDateTime favoritedAt) {
}
