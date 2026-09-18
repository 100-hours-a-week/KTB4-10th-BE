package com.ktb10.kgb.common.security.oauth;

import java.util.Map;
import org.springframework.stereotype.Component;

/** 카카오 사용자 정보 응답을 내부 OAuth 사용자로 변환합니다. */
@Component
public class KakaoOauthUserMapper {

    public KakaoOauthUser map(Map<String, Object> attributes) {
        String subject = text(attributes.get("id"));
        Map<String, Object> account = mapValue(attributes.get("kakao_account"));
        Map<String, Object> profile = mapValue(account.get("profile"));

        return new KakaoOauthUser(
                subject,
                text(profile.get("nickname")),
                text(account.get("email")),
                text(profile.get("profile_image_url")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String converted = value.toString();
        return converted.isBlank() ? null : converted;
    }
}
