package com.ktb10.kgb.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.ktb10.kgb.common.security.oauth.KakaoOauthConfig;
import com.ktb10.kgb.common.security.oauth.KakaoOauthFailureHandler;
import com.ktb10.kgb.common.security.oauth.KakaoOauthSuccessHandler;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 서비스 세션 인증, 공개 경로와 공통 보안 실패 응답을 설정합니다. */
@Configuration
public class SecurityConfig {

    static final RequestMatcher NOTIFICATION_STREAM_ASYNC_DISPATCH =
            new AndRequestMatcher(
                    new DispatcherTypeRequestMatcher(
                            DispatcherType.ASYNC,
                            HttpMethod.GET),
                    PathPatternRequestMatcher.withDefaults().matcher(
                            HttpMethod.GET,
                            "/api/v1/notifications/stream"));

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter,
            ActiveMemberAuthorizationManager activeMemberAuthorizationManager,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<OAuth2AuthorizationRequestResolver> authorizationRequestResolverProvider,
            ObjectProvider<AuthorizationRequestRepository<OAuth2AuthorizationRequest>>
                    authorizationRequestRepositoryProvider,
            ObjectProvider<OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>>
                    accessTokenResponseClientProvider,
            ObjectProvider<OAuth2UserService<OAuth2UserRequest, OAuth2User>>
                    oauth2UserServiceProvider,
            CookieCsrfTokenRepository csrfTokenRepository,
            KakaoOauthSuccessHandler successHandler,
            KakaoOauthFailureHandler failureHandler) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // 최초 REQUEST는 기존 규칙으로 인증·인가하고, 컨테이너 내부 오류
                        // 재디스패치는 이미 커밋된 SSE 응답을 다시 인가하지 않습니다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(NOTIFICATION_STREAM_ASYNC_DISPATCH).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies", "/api/v1/policies/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/members/me",
                                "/api/v1/preference-options",
                                "/api/v1/members/me/preferences",
                                "/api/v1/members/me/settings")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/members/me/preferences")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/members/me/settings")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/members/me")
                        .authenticated()
                        .anyRequest().access(activeMemberAuthorizationManager))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class);

        ClientRegistrationRepository clientRegistrationRepository =
                clientRegistrationRepositoryProvider.getIfAvailable();
        if (clientRegistrationRepository != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestResolver(
                                    authorizationRequestResolverProvider.getObject())
                            .authorizationRequestRepository(
                                    authorizationRequestRepositoryProvider.getObject()))
                    .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(
                            accessTokenResponseClientProvider.getObject()))
                    .userInfoEndpoint(endpoint -> endpoint.userService(
                            oauth2UserServiceProvider.getObject()))
                    .redirectionEndpoint(endpoint -> endpoint
                            .baseUri(KakaoOauthConfig.CALLBACK_BASE_URI + "/*"))
                    .successHandler(successHandler)
                    .failureHandler(failureHandler));
        }

        return http.build();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${SESSION_COOKIE_SECURE:true}") boolean secureCookie) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookie)
                .sameSite("Lax"));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:}") String allowedOriginsValue) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins(allowedOriginsValue));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Content-Type",
                "Idempotency-Key",
                "X-Trace-Id",
                "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static List<String> parseAllowedOrigins(String allowedOriginsValue) {
        return Arrays.stream(allowedOriginsValue.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
