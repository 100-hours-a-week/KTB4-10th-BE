package com.ktb10.kgb.common.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** 공급자 원문 오류를 노출하지 않고 허용된 OAuth 실패 코드로 이동합니다. */
@Component
public class KakaoOauthFailureHandler implements AuthenticationFailureHandler {

    private final String failureRedirectUri;

    public KakaoOauthFailureHandler(
            @Value("${OAUTH_FAILURE_REDIRECT_URI:/api/v1/auth/oauth/error}")
                    String failureRedirectUri) {
        this.failureRedirectUri = failureRedirectUri;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        redirect(response, failureCode(exception));
    }

    void redirect(HttpServletResponse response, OauthErrorCode errorCode) throws IOException {
        String location = UriComponentsBuilder.fromUriString(failureRedirectUri)
                .queryParam("code", errorCode.name())
                .build()
                .toUriString();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.sendRedirect(location);
    }

    private static OauthErrorCode failureCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String providerCode = oauthException.getError().getErrorCode();
            if ("access_denied".equals(providerCode)) {
                return OauthErrorCode.OAUTH_ACCESS_DENIED;
            }
            if ("authorization_request_not_found".equals(providerCode)
                    || "invalid_request".equals(providerCode)) {
                return OauthErrorCode.OAUTH_INVALID_REQUEST;
            }
        }
        return OauthErrorCode.OAUTH_AUTHENTICATION_FAILED;
    }
}
