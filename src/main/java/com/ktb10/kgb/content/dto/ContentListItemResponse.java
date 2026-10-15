package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.repository.ContentListData;

/** 정보 탭 행사 카드 응답입니다. */
public record ContentListItemResponse(
        @JsonProperty("content_id") String contentId,
        String title,
        String category,
        ContentRegionResponse region,
        String address,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("event_period") ContentEventPeriodResponse eventPeriod) {

    public static ContentListItemResponse from(ContentListData data) {
        return new ContentListItemResponse(
                data.contentId(),
                data.title(),
                "EVENT",
                new ContentRegionResponse(data.regionCode(), data.regionName()),
                data.address(),
                data.thumbnailUrl(),
                new ContentEventPeriodResponse(data.startDate(), data.endDate()));
    }
}
