package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 정적 Markdown 정책 상세 응답입니다. */
public record PolicyResponse(
        @JsonProperty("policy_type") String policyType,
        String title,
        String format,
        String content) {
}
