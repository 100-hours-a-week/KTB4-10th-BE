package com.ktb10.kgb.common.security.oauth;

/** 검증된 카카오 사용자 정보를 회원 가입에 필요한 값으로 제한합니다. */
public record KakaoOauthUser(
        String subject,
        String nickname,
        String email,
        String profileImageUrl) {
}
