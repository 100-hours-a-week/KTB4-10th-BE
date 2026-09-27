package com.ktb10.kgb.member.dto.response;

import com.ktb10.kgb.member.entity.MemberStatus;
import java.util.List;

/** 취향 전체 저장 후 선택 집합과 회원 상태입니다. */
public record PreferenceUpdateResponse(
        List<PreferenceSelectionResponse> selections,
        MemberStatus status) {

    public PreferenceUpdateResponse {
        selections = List.copyOf(selections);
    }
}
