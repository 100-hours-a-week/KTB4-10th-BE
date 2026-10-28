package com.ktb10.kgb.guidebook.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** 사용자 세션·CSRF와 분리된 단일 내부 콜백 경로의 Bearer 인증입니다. */
@Configuration
public class GenerationCallbackSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain generationCallbackSecurity(
            HttpSecurity http, GenerationCallbackSettings settings) throws Exception {
        http.securityMatcher("/internal/v1/ai/generation-events")
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/internal/v1/ai/generation-events")
                        .access((authentication, context) -> new AuthorizationDecision(
                                settings.enabled() && matches(
                                        settings.token(),
                                        context.getRequest().getHeader("Authorization"))))
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.setStatus(401))
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(403)));
        return http.build();
    }

    private boolean matches(String expected, String header) {
        return header != null && MessageDigest.isEqual(
                ("Bearer " + expected).getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));
    }
}
