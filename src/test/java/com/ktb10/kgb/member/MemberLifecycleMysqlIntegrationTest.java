package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionCookieResolver;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
@Import(MemberLifecycleMysqlIntegrationTest.FixedClockConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class MemberLifecycleMysqlIntegrationTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-27T06:00:00Z");
    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);
    private static final String RAW_SESSION_ID = "member-lifecycle-session";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void onboardingMemberCompletesPreferencesUsesProtectedApisAndLogsOut() throws Exception {
        Member member = memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                "member-lifecycle",
                "여행자",
                "traveler@example.com",
                null,
                NOW.minusDays(1)));
        AuthSession session = authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash(RAW_SESSION_ID),
                NOW.plusHours(8),
                NOW.minusMinutes(1)));
        Notification notification = notificationRepository.saveAndFlush(Notification.create(
                member,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "생성된 가이드북을 확인해 주세요.",
                null,
                null,
                NOW.minusMinutes(1)));
        Cookie sessionCookie = new Cookie(SessionCookieResolver.COOKIE_NAME, RAW_SESSION_ID);
        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(get("/api/v1/members/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONBOARDING"));
        mockMvc.perform(get("/api/v1/notifications").cookie(sessionCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));

        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPreferenceRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/notifications").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].notification_id")
                        .value(notification.getId().toString()));
        mockMvc.perform(patch("/api/v1/members/me/settings")
                        .cookie(sessionCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"push_enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.push_enabled").value(false));
        mockMvc.perform(delete("/api/v1/notifications/{notificationId}", notification.getId())
                        .cookie(sessionCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        Member activeMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(activeMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(activeMember.isPushEnabled()).isFalse();
        assertThat(memberPreferenceRepository.count()).isEqualTo(3);
        assertThat(notificationRepository.existsById(notification.getId())).isFalse();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessionCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent());
        assertThat(authSessionRepository.findById(session.getId()).orElseThrow().getRevokedAt())
                .isEqualTo(NOW);
        mockMvc.perform(get("/api/v1/members/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
    }

    private static String validPreferenceRequest() {
        return "{\"selections\":["
                + "{\"preference_type\":\"THEME\",\"preference_code\":\"NATURE\"},"
                + "{\"preference_type\":\"DETAIL\","
                + "\"preference_code\":\"NATURE_MOUNTAIN\"},"
                + "{\"preference_type\":\"TRAVEL_STYLE\","
                + "\"preference_code\":\"RELAXING\"}]}";
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }
}
