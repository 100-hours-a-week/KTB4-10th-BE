package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentListProjection;
import java.time.LocalDate;
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

    public record ContentListItemResponse(
            @JsonProperty("content_id") String contentId,
            String title,
            String category,
            ContentRegionResponse region,
            String address,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("event_period") ContentEventPeriodResponse eventPeriod) {

        public static ContentListItemResponse from(ContentListProjection item) {
            return new ContentListItemResponse(
                    item.getContentId(), item.getTitle(), "EVENT",
                    new ContentRegionResponse(item.getRegionCode(), item.getRegionName()),
                    item.getAddress(), item.getThumbnailUrl(),
                    new ContentEventPeriodResponse(item.getStartDate(), item.getEndDate()));
        }
    }

    public record ContentRegionResponse(
            @JsonProperty("administrative_code") String administrativeCode,
            String name) {
    }

    public record ContentEventPeriodResponse(
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate) {
    }
}
