package com.ktb10.kgb.common.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** V1에서 소문자 kakao 경로만 Spring OAuth 인가 요청으로 해석합니다. */
public class KakaoAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    public KakaoAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (!isKakaoPath(request)) {
            return null;
        }
        return delegate.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest request,
            String clientRegistrationId) {
        if (!KakaoOauthConfig.REGISTRATION_ID.equals(clientRegistrationId)) {
            return null;
        }
        return delegate.resolve(request, clientRegistrationId);
    }

    private static boolean isKakaoPath(HttpServletRequest request) {
        return (KakaoOauthConfig.AUTHORIZATION_BASE_URI + "/" + KakaoOauthConfig.REGISTRATION_ID)
                .equals(request.getRequestURI());
    }
}
