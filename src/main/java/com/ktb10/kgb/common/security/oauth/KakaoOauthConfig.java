package com.ktb10.kgb.common.security.oauth;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** 카카오 OAuth 로그인 시작에 필요한 클라이언트와 PKCE 설정을 구성합니다. */
@Configuration
@ConditionalOnProperty(name = "KAKAO_REST_API_KEY")
public class KakaoOauthConfig {

    public static final String REGISTRATION_ID = "kakao";
    public static final String AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth/authorize";
    public static final String CALLBACK_BASE_URI = "/api/v1/auth/oauth/callback";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${KAKAO_REST_API_KEY}") String clientId,
            @Value("${KAKAO_CLIENT_SECRET}") String clientSecret,
            @Value("${KAKAO_REDIRECT_URI}") String redirectUri,
            @Value("${KAKAO_AUTHORIZATION_URI:https://kauth.kakao.com/oauth/authorize}")
                    String authorizationUri,
            @Value("${KAKAO_TOKEN_URI:https://kauth.kakao.com/oauth/token}") String tokenUri,
            @Value("${KAKAO_USER_INFO_URI:https://kapi.kakao.com/v2/user/me}")
                    String userInfoUri) {
        ClientRegistration kakao = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .userNameAttributeName("id")
                .clientName("Kakao")
                .clientSettings(ClientRegistration.ClientSettings.builder()
                        .requireProofKey(true)
                        .build())
                .build();
        return new InMemoryClientRegistrationRepository(kakao);
    }

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        AUTHORIZATION_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        return new KakaoAuthorizationRequestResolver(resolver);
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest>
            authorizationRequestRepository(Clock clock) {
        return new ExpiringAuthorizationRequestRepository(clock);
    }
}
