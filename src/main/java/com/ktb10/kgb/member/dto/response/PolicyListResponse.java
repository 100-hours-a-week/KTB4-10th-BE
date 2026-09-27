package com.ktb10.kgb.member.dto.response;

import java.util.List;

/** 공개 정책 목록 응답입니다. */
public record PolicyListResponse(List<PolicySummaryResponse> items) {

    public PolicyListResponse {
        items = List.copyOf(items);
    }
}
