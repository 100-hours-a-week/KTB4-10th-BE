package com.ktb10.kgb.content.tourapi;

import java.time.LocalDate;

/** TourAPI의 전국 콘텐츠와 기간별 행사 원본 응답을 조회합니다. */
public interface TourApiClient {

    byte[] fetchAreaSnapshot();

    byte[] fetchFestivalSnapshot(LocalDate startDate, LocalDate endDate);
}
