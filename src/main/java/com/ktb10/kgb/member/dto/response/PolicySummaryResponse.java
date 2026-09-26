package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 정책 목록에 표시할 코드와 제목입니다. */
public record PolicySummaryResponse(
        @JsonProperty("policy_type") String policyType,
        String title) {
}
