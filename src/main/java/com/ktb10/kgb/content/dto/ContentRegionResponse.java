package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 관광 콘텐츠 지역 정보입니다. */
public record ContentRegionResponse(
        @JsonProperty("administrative_code") String administrativeCode,
        String name) {
}
