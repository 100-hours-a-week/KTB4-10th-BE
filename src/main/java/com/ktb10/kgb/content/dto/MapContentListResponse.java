package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 현재 지도 영역의 바텀시트 콘텐츠 페이지입니다. */
public record MapContentListResponse(
        List<MapContentItemResponse> items,
        @JsonProperty("next_cursor")
        String nextCursor,
        @JsonProperty("has_more")
        boolean hasMore) {

    public MapContentListResponse {
        items = List.copyOf(items);
    }
}
