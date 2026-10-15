package com.ktb10.kgb.content.repository;

import java.time.LocalDate;

/** 행사 목록 조회 결과입니다. */
public record ContentListData(
        String contentId,
        String title,
        String regionCode,
        String regionName,
        String address,
        String thumbnailUrl,
        LocalDate startDate,
        LocalDate endDate) {
}
