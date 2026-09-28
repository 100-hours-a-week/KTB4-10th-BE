package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 관심 장소 커서 목록 응답입니다. */
public record FavoriteContentListResponse(
        List<FavoriteContentItemResponse> items,
        @JsonProperty("next_cursor")
        String nextCursor,
        @JsonProperty("has_more")
        boolean hasMore) {

    public FavoriteContentListResponse {
        items = List.copyOf(items);
    }
}
