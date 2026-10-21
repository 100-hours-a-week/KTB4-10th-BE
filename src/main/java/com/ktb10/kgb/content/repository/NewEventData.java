package com.ktb10.kgb.content.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 특정 일자에 TourAPI에 신규 등록된 행사 목록 항목입니다. */
public record NewEventData(
        String sourceContentId,
        String title,
        String address,
        String thumbnailUrl,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime sourceCreatedAt) {
}
