package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventOverview;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import com.ktb10.kgb.content.dto.MapMarkerItemResponse;

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
        String overview,
        String phone,
        String homepageUrl,
        EventOverview eventDetail,
        EventPeriod eventPeriod) {

    public MapContentCommonData(
            Long id,
            String contentId,
            String title,
            ContentType contentType,
            String address,
            double latitude,
            double longitude,
            String thumbnailUrl,
            EventPeriod eventPeriod) {
        this(id, contentId, title, contentType, address, latitude, longitude, thumbnailUrl,
                null, null, null, null, eventPeriod);
    }

    public MapContentItemResponse toResponse(boolean favorite, boolean inGuidebook) {
        return new MapContentItemResponse(
                contentId,
                title,
                contentType,
                address,
                latitude,
                longitude,
                thumbnailUrl,
                overview,
                phone,
                homepageUrl,
                eventPeriod,
                eventDetail,
                favorite,
                inGuidebook);
    }

    public MapMarkerItemResponse toMarkerResponse(boolean favorite, boolean inGuidebook) {
        return new MapMarkerItemResponse(
                contentId,
                title,
                contentType,
                latitude,
                longitude,
                thumbnailUrl,
                favorite,
                inGuidebook);
    }
}
