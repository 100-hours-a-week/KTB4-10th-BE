package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.security.CsrfTokenLifecycle;
import com.ktb10.kgb.common.security.SessionCookieManager;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class KakaoOauthSuccessHandlerTest {

    @Test
    void issuesServiceCookieInvalidatesTemporarySessionAndRedirects() throws Exception {
        OauthLoginService loginService = mock(OauthLoginService.class);
        when(loginService.login(any())).thenReturn(new OauthLoginResult(
                "raw-service-session",
                MemberStatus.ONBOARDING));
        KakaoOauthFailureHandler failureHandler =
                new KakaoOauthFailureHandler("/api/v1/auth/oauth/error");
        CsrfTokenLifecycle csrfTokenLifecycle = mock(CsrfTokenLifecycle.class);
        KakaoOauthSuccessHandler handler = new KakaoOauthSuccessHandler(
                new KakaoOauthUserMapper(),
                loginService,
                failureHandler,
                csrfTokenLifecycle,
                new SessionCookieManager(false),
                "/api/v1/members/me");
        var principal = new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "id", 123L,
                        "kakao_account", Map.of(
                                "profile", Map.of("nickname", "여행자"))),
                "id");
        var authentication = new OAuth2AuthenticationToken(principal, List.of(), "kakao");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/api/v1/members/me");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("KGB_SESSION=raw-service-session")
                .contains("Max-Age=28800")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Secure");
        assertThat(request.getSession(false)).isNull();
        verify(csrfTokenLifecycle).clear(request, response);
    }
}
