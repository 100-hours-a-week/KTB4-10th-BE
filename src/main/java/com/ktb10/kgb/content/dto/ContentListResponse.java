package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 행사 목록의 번호 페이지 응답입니다. */
public record ContentListResponse(
        List<ContentListItemResponse> items,
        int page,
        int size,
        @JsonProperty("total_items") long totalItems,
        @JsonProperty("total_pages") int totalPages) {

    public ContentListResponse {
        items = List.copyOf(items);
    }
}
