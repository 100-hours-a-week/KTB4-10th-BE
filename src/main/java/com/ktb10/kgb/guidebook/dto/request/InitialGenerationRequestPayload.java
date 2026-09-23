package com.ktb10.kgb.guidebook.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.MemberPreference;
import java.util.List;

/** 최초 가이드북 생성 접수 시점의 요청과 회원 기본 취향을 보존하는 스냅샷입니다. */
public record InitialGenerationRequestPayload(
        @JsonProperty("request")
        GuidebookGenerationRequest request,

        @JsonProperty("preferences")
        List<PreferenceSnapshot> preferences) {

    public InitialGenerationRequestPayload {
        preferences = List.copyOf(preferences);
    }

    public static InitialGenerationRequestPayload from(
            GuidebookGenerationRequest request,
            List<MemberPreference> memberPreferences) {
        List<PreferenceSnapshot> preferences = memberPreferences.stream()
                .map(PreferenceSnapshot::from)
                .toList();
        return new InitialGenerationRequestPayload(request, preferences);
    }

    public record PreferenceSnapshot(
            @JsonProperty("preference_type")
            String preferenceType,

            @JsonProperty("preference_code")
            String preferenceCode) {

        private static PreferenceSnapshot from(MemberPreference preference) {
            return new PreferenceSnapshot(
                    preference.getPreferenceType().name(),
                    preference.getPreferenceCode().name());
        }
    }
}
