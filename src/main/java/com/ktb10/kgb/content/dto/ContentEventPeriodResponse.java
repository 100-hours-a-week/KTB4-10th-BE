package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** 행사 목록의 기간 정보입니다. */
public record ContentEventPeriodResponse(
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate) {
}
