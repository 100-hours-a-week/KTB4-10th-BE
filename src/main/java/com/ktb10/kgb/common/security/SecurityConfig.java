package com.ktb10.kgb.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.ktb10.kgb.common.error.CommonErrorCode;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** 서비스 세션 인증, 공개 경로와 공통 보안 실패 응답을 설정합니다. */
@Configuration
public class SecurityConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            RestSecurityErrorWriter errorWriter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<OAuth2AuthorizationRequestResolver> authorizationRequestResolverProvider,
            ObjectProvider<AuthorizationRequestRepository<OAuth2AuthorizationRequest>>
                    authorizationRequestRepositoryProvider) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies", "/api/v1/policies/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class);

        ClientRegistrationRepository clientRegistrationRepository =
                clientRegistrationRepositoryProvider.getIfAvailable();
        if (clientRegistrationRepository != null) {
            OAuth2AuthorizationRequestRedirectFilter authorizationRedirectFilter =
                    new OAuth2AuthorizationRequestRedirectFilter(
                            authorizationRequestResolverProvider.getObject());
            authorizationRedirectFilter.setAuthorizationRequestRepository(
                    authorizationRequestRepositoryProvider.getObject());
            authorizationRedirectFilter.setAuthenticationFailureHandler(
                    (request, response, exception) ->
                            errorWriter.write(request, response, CommonErrorCode.COMMON_VALIDATION_ERROR));
            http.addFilterBefore(authorizationRedirectFilter, SessionAuthenticationFilter.class);
        }

        return http.build();
    }
}
