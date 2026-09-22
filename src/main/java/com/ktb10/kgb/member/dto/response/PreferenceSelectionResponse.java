package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.MemberPreference;

/** 회원이 현재 선택한 하나의 취향 코드입니다. */
public record PreferenceSelectionResponse(
        @JsonProperty("preference_type")
        String preferenceType,

        @JsonProperty("preference_code")
        String preferenceCode) {

    public static PreferenceSelectionResponse from(MemberPreference preference) {
        return new PreferenceSelectionResponse(
                preference.getPreferenceType().name(),
                preference.getPreferenceCode().name());
    }
}
