package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.dto.ContentListResponse.ContentRegionResponse;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentDetailProjection;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentImageProjection;
import java.time.LocalDate;
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
        @JsonProperty("is_favorite") boolean favorite,
        @JsonProperty("event_detail") ContentEventDetailResponse eventDetail) {

    public ContentDetailResponse {
        images = List.copyOf(images);
    }

    public static ContentDetailResponse from(
            ContentDetailProjection data,
            List<ContentImageProjection> images,
            boolean favorite) {
        return new ContentDetailResponse(
                data.getContentId(),
                data.getTitle(),
                "EVENT",
                new ContentRegionResponse(data.getRegionCode(), data.getRegionName()),
                data.getLatitude(),
                data.getLongitude(),
                data.getThumbnailUrl(),
                data.getDescription(),
                data.getAddress(),
                data.getPhone(),
                data.getHomepageUrl(),
                images.stream().map(ContentImageResponse::from).toList(),
                favorite,
                new ContentEventDetailResponse(
                        data.getStartDate(),
                        data.getEndDate(),
                        data.getOperatingHours(),
                        data.getOrganizer(),
                        null,
                        data.getAddress()));
    }

    public record ContentImageResponse(
            @JsonProperty("image_id") String imageId,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("sort_order") int sortOrder) {

        public static ContentImageResponse from(ContentImageProjection image) {
            return new ContentImageResponse(
                    image.getImageId(), image.getImageUrl(), image.getSortOrder());
        }
    }

    public record ContentEventDetailResponse(
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("operating_hours") String operatingHours,
            String organizer,
            @JsonProperty("organizer_phone") String organizerPhone,
            @JsonProperty("event_place") String eventPlace) {
    }
}
