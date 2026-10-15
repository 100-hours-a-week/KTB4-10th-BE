package com.ktb10.kgb.content.repository;

import java.time.LocalDate;

/** 행사 상세 조회 결과입니다. */
public record ContentDetailData(
        long id,
        String contentId,
        String title,
        String regionCode,
        String regionName,
        double latitude,
        double longitude,
        String thumbnailUrl,
        String description,
        String address,
        String phone,
        String homepageUrl,
        LocalDate startDate,
        LocalDate endDate,
        String operatingHours,
        String organizer) {
}
