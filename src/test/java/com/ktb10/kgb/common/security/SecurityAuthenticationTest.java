package com.ktb10.kgb.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.error.TraceId;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "CORS_ALLOWED_ORIGINS=https://frontend.example,http://localhost:3000"
})
@AutoConfigureMockMvc
@Import({SecurityAuthenticationTest.SecurityTestConfiguration.class,
        SecurityAuthenticationTest.SecurityTestController.class})
class SecurityAuthenticationTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-17T05:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void cleanUp() {
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void protectedPathWithoutSessionReturnsCommonUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/test/principal"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TraceId.HEADER_NAME))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value((Object) null))
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"))
                .andExpect(jsonPath("$.error.trace_id").isNotEmpty());
    }

    @Test
    void validSessionExposesAuthenticatedMemberPrincipal() throws Exception {
        Member member = memberRepository.save(member("principal-member"));
        AuthSession session = authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash("principal-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10)));

        mockMvc.perform(get("/api/v1/test/principal")
                        .cookie(new Cookie(SessionCookieResolver.COOKIE_NAME, "principal-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member_id").value(member.getId()))
                .andExpect(jsonPath("$.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.session_id").value(session.getId()));
    }

    @Test
    void unknownSessionCannotAccessProtectedPathButDoesNotBlockPublicPath() throws Exception {
        Cookie unknownSession = new Cookie(SessionCookieResolver.COOKIE_NAME, "unknown-session");

        mockMvc.perform(get("/api/v1/test/principal").cookie(unknownSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));

        mockMvc.perform(get("/api/v1/policies/test-public").cookie(unknownSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("public"));
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenReturnsCommonForbiddenResponse() throws Exception {
        Member member = memberRepository.save(member("csrf-member"));
        authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash("csrf-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10)));

        mockMvc.perform(post("/api/v1/test/protected-change")
                        .cookie(new Cookie(SessionCookieResolver.COOKIE_NAME, "csrf-session"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void csrfEndpointIssuesReadableCookieAndReturnsHeaderContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("XSRF-TOKEN=")))
                .andExpect(jsonPath("$.message").value("CSRF 토큰 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.cookie_name").value("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.header_name").value("X-XSRF-TOKEN"))
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(csrfCookie.getSecure()).isTrue();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
    }

    @Test
    void stateChangingRequestWithCookieTokenAndHeaderSucceeds() throws Exception {
        Member member = memberRepository.save(member("valid-csrf-member"));
        authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash("valid-csrf-session"),
                NOW.plusHours(1),
                NOW.minusMinutes(10)));
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/v1/test/protected-change")
                        .header("Origin", "http://localhost:3000")
                        .cookie(
                                new Cookie(SessionCookieResolver.COOKIE_NAME, "valid-csrf-session"),
                                csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("Idempotency-Key", "browser-request-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(jsonPath("$.status").value("changed"));
    }

    @Test
    void stateChangingRequestWithTamperedCsrfTokenReturnsCommonForbiddenResponse() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/v1/test/protected-change")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "변조된-토큰"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void corsAllowsConfiguredCredentialOriginAndRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/test/protected-change")
                        .header("Origin", "https://frontend.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header(
                                "Access-Control-Request-Headers",
                                "X-XSRF-TOKEN, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("X-XSRF-TOKEN")))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("Idempotency-Key")));

        mockMvc.perform(options("/api/v1/test/protected-change")
                        .header("Origin", "https://unknown.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private static Member member(String oauthSubject) {
        return Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
    }

    @TestConfiguration
    static class SecurityTestConfiguration {

        @Bean
        @Primary
        Clock fixedSecurityClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/api/v1/test/principal")
        Map<String, Object> principal(@AuthenticationPrincipal AuthenticatedMember member) {
            return Map.of(
                    "member_id", member.memberId(),
                    "status", member.status(),
                    "session_id", member.sessionId());
        }

        @PostMapping("/api/v1/test/protected-change")
        Map<String, String> protectedChange() {
            return Map.of("status", "changed");
        }

        @GetMapping("/api/v1/policies/test-public")
        Map<String, String> publicPath() {
            return Map.of("status", "public");
        }
    }
}
