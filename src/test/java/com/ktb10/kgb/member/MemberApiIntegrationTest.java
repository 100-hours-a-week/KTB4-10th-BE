package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionCookieResolver;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
@Import(MemberApiIntegrationTest.FixedClockConfiguration.class)
class MemberApiIntegrationTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-20T03:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void getMeReturnsProfileAndUnreadNotificationCount() throws Exception {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "member-me",
                "여행자",
                null,
                "https://example.com/profile.png",
                NOW.minusDays(1)));
        issueSession(member, "member-me-session");
        notificationRepository.save(Notification.create(
                member,
                NotificationType.GUIDEBOOK_COMPLETED,
                "생성 완료",
                "가이드북 생성이 완료되었습니다.",
                null,
                null,
                NOW.minusMinutes(1)));

        mockMvc.perform(get("/api/v1/members/me")
                        .cookie(sessionCookie("member-me-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("member_get_success"))
                .andExpect(jsonPath("$.data.member_id").value(member.getId()))
                .andExpect(jsonPath("$.data.nickname").value("여행자"))
                .andExpect(jsonPath("$.data.email").value((Object) null))
                .andExpect(jsonPath("$.data.profile_image_url")
                        .value("https://example.com/profile.png"))
                .andExpect(jsonPath("$.data.language_code").value("ko"))
                .andExpect(jsonPath("$.data.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.data.unread_count").value(1));
    }

    @Test
    void logoutRevokesCurrentSessionAndExpiresSessionAndCsrfCookies() throws Exception {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "logout-member",
                "여행자",
                "member@example.com",
                null,
                NOW.minusDays(1)));
        AuthSession session = issueSession(member, "logout-session");
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessionCookie("logout-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logoutResult.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.startsWith("XSRF-TOKEN="))
                .anyMatch(value -> value.startsWith("KGB_SESSION=;"));

        AuthSession revokedSession = authSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(revokedSession.getRevokedAt()).isEqualTo(NOW);

        mockMvc.perform(get("/api/v1/members/me")
                        .cookie(sessionCookie("logout-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @Test
    void getMeAndLogoutRequireAuthenticationAndLogoutRequiresCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @Test
    void preferenceOptionsFollowApprovedOrderForOnboardingMember() throws Exception {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                "preference-options-member",
                "여행자",
                null,
                null,
                NOW.minusDays(1)));
        issueSession(member, "preference-options-session");

        mockMvc.perform(get("/api/v1/preference-options")
                        .cookie(sessionCookie("preference-options-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("preference_option_list_success"))
                .andExpect(jsonPath("$.data.items.length()").value(37))
                .andExpect(jsonPath("$.data.items[0].preference_type").value("THEME"))
                .andExpect(jsonPath("$.data.items[0].code").value("NATURE"))
                .andExpect(jsonPath("$.data.items[0].label").value("자연"))
                .andExpect(jsonPath("$.data.items[0].parent_code").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].sort_order").value(10))
                .andExpect(jsonPath("$.data.items[1].code").value("NATURE_MOUNTAIN"))
                .andExpect(jsonPath("$.data.items[1].parent_code").value("NATURE"))
                .andExpect(jsonPath("$.data.items[33].code").value("RELAXING"))
                .andExpect(jsonPath("$.data.items[33].sort_order").value(10))
                .andExpect(jsonPath("$.data.items[36].code").value("CAR_TRAVEL"));
    }

    @Test
    void preferenceOptionsAllowActiveMemberAndRejectAnonymousRequest() throws Exception {
        Member member = Member.register(
                OauthProvider.KAKAO,
                "active-preference-member",
                "여행자",
                null,
                null,
                NOW.minusDays(1));
        member.activate(NOW.minusHours(1));
        memberRepository.save(member);
        issueSession(member, "active-preference-session");

        mockMvc.perform(get("/api/v1/preference-options")
                        .cookie(sessionCookie("active-preference-session")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/preference-options"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    private AuthSession issueSession(Member member, String rawSessionId) {
        return authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash(rawSessionId),
                NOW.plusHours(8),
                NOW.minusMinutes(1)));
    }

    private static Cookie sessionCookie(String value) {
        return new Cookie(SessionCookieResolver.COOKIE_NAME, value);
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
