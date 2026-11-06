package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.repository.ContentSearchQuery.ContentSearchItem;
import com.ktb10.kgb.content.repository.MapContentPersonalization;
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
            @JsonProperty("content_type") String contentType,
            ContentRegionResponse region,
            String address,
            double latitude,
            double longitude,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("event_period") ContentEventPeriodResponse eventPeriod,
            @JsonProperty("is_favorite") boolean favorite,
            @JsonProperty("is_in_guidebook") boolean inGuidebook) {

        public static ContentListItemResponse from(
                ContentSearchItem item,
                MapContentPersonalization personalization) {
            ContentEventPeriodResponse period = item.startDate() == null
                    ? null
                    : new ContentEventPeriodResponse(item.startDate(), item.endDate());
            return new ContentListItemResponse(
                    item.contentId(), item.title(), item.contentType(), item.contentType(),
                    new ContentRegionResponse(item.regionCode(), item.regionName()),
                    item.address(), item.latitude(), item.longitude(), item.thumbnailUrl(), period,
                    personalization.isFavorite(item.id()),
                    personalization.isInGuidebook(item.id()));
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
