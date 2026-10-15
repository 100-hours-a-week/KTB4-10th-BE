package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.content.repository.ContentImageData;

/** 행사 상세 이미지 응답입니다. */
public record ContentImageResponse(
        @JsonProperty("image_id") String imageId,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("sort_order") int sortOrder) {

    public static ContentImageResponse from(ContentImageData data) {
        return new ContentImageResponse(data.imageId(), data.imageUrl(), data.sortOrder());
    }
}
