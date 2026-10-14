package com.ktb10.kgb.content.tourapi;

/** 상세 API 응답을 반영할 서비스 콘텐츠 식별 정보입니다. */
public record TourApiDetailTarget(
        long id,
        String sourceContentId,
        String sourceContentTypeId,
        boolean event) {
}
