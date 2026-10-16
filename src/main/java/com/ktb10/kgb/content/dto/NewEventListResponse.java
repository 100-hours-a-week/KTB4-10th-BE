package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/** 특정 일자에 신규 등록된 행사의 번호 페이지입니다. */
public record NewEventListResponse(
        LocalDate date,
        List<NewEventItemResponse> items,
        int page,
        int size,
        @JsonProperty("total_items")
        long totalItems,
        @JsonProperty("total_pages")
        int totalPages) {

    public NewEventListResponse {
        items = List.copyOf(items);
    }
}
