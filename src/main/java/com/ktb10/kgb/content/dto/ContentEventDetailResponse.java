package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** 행사 전용 상세 정보 응답입니다. */
public record ContentEventDetailResponse(
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("operating_hours") String operatingHours,
        String organizer,
        @JsonProperty("organizer_phone") String organizerPhone,
        @JsonProperty("event_place") String eventPlace) {
}
