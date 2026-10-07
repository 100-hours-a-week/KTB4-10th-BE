package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;

/** 사용자와 무관하게 모든 요청에서 공유할 수 있는 지도 콘텐츠 데이터입니다. */
public record MapContentCommonData(
        Long id,
        String contentId,
        String title,
        ContentType contentType,
        String address,
        double latitude,
        double longitude,
        String thumbnailUrl,
        EventPeriod eventPeriod) {

    public MapContentItemResponse toResponse(boolean favorite, boolean inGuidebook) {
        return new MapContentItemResponse(
                contentId,
                title,
                contentType,
                address,
                latitude,
                longitude,
                thumbnailUrl,
                eventPeriod,
                favorite,
                inGuidebook);
    }
}
