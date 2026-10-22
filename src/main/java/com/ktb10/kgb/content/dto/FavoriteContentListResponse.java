package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import com.ktb10.kgb.content.repository.FavoriteContentQuery.FavoriteContentListRow;
import java.util.List;

/** 로그인 회원이 저장한 관광 콘텐츠 목록 응답입니다. */
public record FavoriteContentListResponse(
        List<FavoriteContentListItemResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore) {

    public FavoriteContentListResponse {
        items = List.copyOf(items);
    }

    public record FavoriteContentListItemResponse(
            @JsonProperty("content_id") String contentId,
            String title,
            @JsonProperty("content_type") ContentType contentType,
            String address,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("event_period") EventPeriod eventPeriod,
            @JsonProperty("is_favorite") boolean favorite) {

        public static FavoriteContentListItemResponse from(FavoriteContentListRow row) {
            EventPeriod period = row.contentType() == ContentType.EVENT
                    ? new EventPeriod(row.eventStartDate(), row.eventEndDate())
                    : null;
            return new FavoriteContentListItemResponse(
                    row.contentId(),
                    row.title(),
                    row.contentType(),
                    row.address(),
                    row.thumbnailUrl(),
                    period,
                    true);
        }
    }
}
