package com.ktb10.kgb.member.dto.response;

import java.util.List;

/** 현재 회원의 전체 취향 선택 집합입니다. */
public record MemberPreferenceResponse(List<PreferenceSelectionResponse> selections) {

    public MemberPreferenceResponse {
        selections = List.copyOf(selections);
    }
}
