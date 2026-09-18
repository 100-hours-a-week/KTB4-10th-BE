package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class KakaoOauthFailureHandlerTest {

    private final KakaoOauthFailureHandler handler =
            new KakaoOauthFailureHandler("/api/v1/auth/oauth/error");

    @Test
    void mapsTokenConnectionFailureToProviderUnavailable() throws Exception {
        OAuth2AuthenticationException exception = authenticationException(
                "invalid_token_response",
                new ResourceAccessException("연결 시간 초과"));

        assertFailureRedirect(exception, OauthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
    }

    @Test
    void mapsUserInfoServerErrorToProviderUnavailable() throws Exception {
        OAuth2AuthenticationException exception = authenticationException(
                "invalid_user_info_response",
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertFailureRedirect(exception, OauthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
    }

    @Test
    void mapsProviderTemporaryErrorToProviderUnavailable() throws Exception {
        OAuth2AuthorizationException providerFailure = new OAuth2AuthorizationException(
                new OAuth2Error("temporarily_unavailable"));
        OAuth2AuthenticationException exception = authenticationException(
                "invalid_token_response",
                providerFailure);

        assertFailureRedirect(exception, OauthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
    }

    @Test
    void keepsInvalidGrantAsAuthenticationFailure() throws Exception {
        OAuth2AuthenticationException exception = authenticationException("invalid_grant", null);

        assertFailureRedirect(exception, OauthErrorCode.OAUTH_AUTHENTICATION_FAILED);
    }

    private void assertFailureRedirect(
            OAuth2AuthenticationException exception,
            OauthErrorCode expectedCode) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/api/v1/auth/oauth/error?code=" + expectedCode.name());
    }

    private static OAuth2AuthenticationException authenticationException(
            String errorCode,
            Throwable cause) {
        OAuth2Error error = new OAuth2Error(errorCode);
        return new OAuth2AuthenticationException(error, error.toString(), cause);
    }
}
