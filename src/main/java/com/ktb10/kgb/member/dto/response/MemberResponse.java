package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;

/** 내 회원 정보 조회 응답입니다. */
public record MemberResponse(
        @JsonProperty("member_id")
        Long memberId,
        String nickname,
        String email,
        @JsonProperty("profile_image_url")
        String profileImageUrl,
        @JsonProperty("language_code")
        String languageCode,
        MemberStatus status,
        @JsonProperty("unread_count")
        long unreadCount) {

    public static MemberResponse of(Member member, long unreadCount) {
        return new MemberResponse(
                member.getId(),
                member.getNickname(),
                member.getEmail(),
                member.getProfileImageUrl(),
                member.getLanguageCode(),
                member.getStatus(),
                unreadCount);
    }
}
