package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.Member;

/** 회원의 표시 언어와 푸시 수신 설정 응답입니다. */
public record MemberSettingsResponse(
        @JsonProperty("language_code")
        String languageCode,
        @JsonProperty("push_enabled")
        boolean pushEnabled) {

    public static MemberSettingsResponse from(Member member) {
        return new MemberSettingsResponse(
                member.getLanguageCode(),
                member.isPushEnabled());
    }
}
