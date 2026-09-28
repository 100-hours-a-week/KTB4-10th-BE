package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 지도 범위에 포함된 콘텐츠 목록과 추가 결과 여부입니다. */
public record MapContentResponse(
        List<MapContentItemResponse> items,
        @JsonProperty("has_more")
        boolean hasMore) {

    public MapContentResponse {
        items = List.copyOf(items);
    }
}

