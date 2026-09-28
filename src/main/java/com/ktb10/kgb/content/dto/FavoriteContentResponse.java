package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 관심 장소 등록 상태입니다. */
public record FavoriteContentResponse(
        @JsonProperty("content_id")
        String contentId,
        @JsonProperty("is_favorite")
        boolean favorite) {
}
