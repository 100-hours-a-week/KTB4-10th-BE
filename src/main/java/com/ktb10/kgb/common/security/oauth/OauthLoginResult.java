package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.member.entity.MemberStatus;

/** OAuth 로그인 완료 후 브라우저에 전달할 서비스 세션과 회원 상태입니다. */
public record OauthLoginResult(String rawSessionId, MemberStatus memberStatus) {
}
