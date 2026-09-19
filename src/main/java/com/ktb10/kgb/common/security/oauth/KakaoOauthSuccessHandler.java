package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.security.CsrfTokenLifecycle;
import com.ktb10.kgb.common.security.SessionCookieResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** 카카오 로그인 성공을 서비스 회원·세션으로 전환하고 브라우저 쿠키를 발급합니다. */
@Component
public class KakaoOauthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration COOKIE_MAX_AGE = Duration.ofHours(8);
    private static final Logger LOGGER = LoggerFactory.getLogger(KakaoOauthSuccessHandler.class);

    private final KakaoOauthUserMapper userMapper;
    private final OauthLoginService loginService;
    private final KakaoOauthFailureHandler failureHandler;
    private final CsrfTokenLifecycle csrfTokenLifecycle;
    private final String successRedirectUri;
    private final boolean secureCookie;

    public KakaoOauthSuccessHandler(
            KakaoOauthUserMapper userMapper,
            OauthLoginService loginService,
            KakaoOauthFailureHandler failureHandler,
            CsrfTokenLifecycle csrfTokenLifecycle,
            @Value("${OAUTH_SUCCESS_REDIRECT_URI:/api/v1/members/me}") String successRedirectUri,
            @Value("${SESSION_COOKIE_SECURE:true}") boolean secureCookie) {
        this.userMapper = userMapper;
        this.loginService = loginService;
        this.failureHandler = failureHandler;
        this.csrfTokenLifecycle = csrfTokenLifecycle;
        this.successRedirectUri = successRedirectUri;
        this.secureCookie = secureCookie;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        try {
            completeLogin(request, response, authentication);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "OAuth 회원·세션 처리 실패: {}",
                    exception.getClass().getSimpleName());
            failureHandler.redirect(response, OauthErrorCode.OAUTH_INTERNAL_ERROR);
        }
    }

    private void completeLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = requireKakaoAuthentication(authentication);
        OauthLoginResult result = loginService.login(userMapper.map(
                oauthToken.getPrincipal().getAttributes()));

        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        csrfTokenLifecycle.clear(request, response);
        ResponseCookie sessionCookie = ResponseCookie.from(
                        SessionCookieResolver.COOKIE_NAME,
                        result.rawSessionId())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.sendRedirect(successRedirectUri);
    }

    private static OAuth2AuthenticationToken requireKakaoAuthentication(
            Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !KakaoOauthConfig.REGISTRATION_ID.equals(token.getAuthorizedClientRegistrationId())) {
            throw new IllegalArgumentException("지원하지 않는 OAuth 인증 결과입니다.");
        }
        return token;
    }
}
