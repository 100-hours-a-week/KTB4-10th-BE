package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;

/** 상세 줌 지도에 표시할 최소 관광 콘텐츠 정보입니다. */
public record MapMarkerItemResponse(
        @JsonProperty("content_id")
        String contentId,
        String title,
        @JsonProperty("content_type")
        ContentType contentType,
        double latitude,
        double longitude,
        @JsonProperty("is_favorite")
        boolean favorite,
        @JsonProperty("is_in_guidebook")
        boolean inGuidebook) {
}
