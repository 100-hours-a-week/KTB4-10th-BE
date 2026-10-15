package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.repository.ContentDetailData;
import com.ktb10.kgb.content.repository.ContentImageData;
import java.util.List;

/** 행사 상세 페이지 응답입니다. */
public record ContentDetailResponse(
        @JsonProperty("content_id") String contentId,
        String title,
        String category,
        ContentRegionResponse region,
        double latitude,
        double longitude,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        String description,
        String address,
        String phone,
        @JsonProperty("homepage_url") String homepageUrl,
        List<ContentImageResponse> images,
        @JsonProperty("event_detail") ContentEventDetailResponse eventDetail) {

    public ContentDetailResponse {
        images = List.copyOf(images);
    }

    public static ContentDetailResponse from(
            ContentDetailData data,
            List<ContentImageData> images) {
        return new ContentDetailResponse(
                data.contentId(),
                data.title(),
                "EVENT",
                new ContentRegionResponse(data.regionCode(), data.regionName()),
                data.latitude(),
                data.longitude(),
                data.thumbnailUrl(),
                data.description(),
                data.address(),
                data.phone(),
                data.homepageUrl(),
                images.stream().map(ContentImageResponse::from).toList(),
                new ContentEventDetailResponse(
                        data.startDate(),
                        data.endDate(),
                        data.operatingHours(),
                        data.organizer(),
                        null,
                        data.address()));
    }
}
