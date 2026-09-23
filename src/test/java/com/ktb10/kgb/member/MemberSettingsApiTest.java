package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-settings-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
@Import(MemberSettingsApiTest.FixedClockConfiguration.class)
class MemberSettingsApiTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-23T03:00:00Z");
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
    void onboardingMemberCanGetCurrentSettings() throws Exception {
        Member member = saveMember("settings-get-member", MemberStatus.ONBOARDING);
        issueSession(member, "settings-get-session");

        mockMvc.perform(get("/api/v1/members/me/settings")
                        .cookie(sessionCookie("settings-get-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("member_setting_get_success"))
                .andExpect(jsonPath("$.data.language_code").value("ko"))
                .andExpect(jsonPath("$.data.push_enabled").value(true));
    }

    @Test
    void activeMemberCanUpdatePushSettingWithoutDeletingInAppNotifications() throws Exception {
        Member member = saveMember("settings-update-member", MemberStatus.ACTIVE);
        issueSession(member, "settings-update-session");
        notificationRepository.save(Notification.create(
                member,
                NotificationType.GUIDEBOOK_COMPLETED,
                "생성 완료",
                "가이드북 생성이 완료되었습니다.",
                null,
                null,
                NOW.minusMinutes(1)));
        Csrf csrf = csrf();

        mockMvc.perform(patch("/api/v1/members/me/settings")
                        .cookie(sessionCookie("settings-update-session"), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"push_enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("member_setting_update_success"))
                .andExpect(jsonPath("$.data.language_code").value("ko"))
                .andExpect(jsonPath("$.data.push_enabled").value(false));

        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.isPushEnabled()).isFalse();
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
        assertThat(notificationRepository.countByRecipientId(member.getId())).isOne();
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void updateSettingsRejectsInvalidBody(String body) throws Exception {
        Member member = saveMember("settings-invalid-member", MemberStatus.ONBOARDING);
        issueSession(member, "settings-invalid-session");
        Csrf csrf = csrf();

        mockMvc.perform(patch("/api/v1/members/me/settings")
                        .cookie(sessionCookie("settings-invalid-session"), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().isPushEnabled()).isTrue();
    }

    @Test
    void settingsRequireAuthenticationAndUpdateRequiresCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));

        Member member = saveMember("settings-csrf-member", MemberStatus.ONBOARDING);
        issueSession(member, "settings-csrf-session");
        mockMvc.perform(patch("/api/v1/members/me/settings")
                        .cookie(sessionCookie("settings-csrf-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"push_enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    private static Stream<String> invalidBodies() {
        return Stream.of(
                "{}",
                "{\"push_enabled\":null}",
                "[]",
                "{");
    }

    private Member saveMember(String oauthSubject, MemberStatus status) {
        Member member = Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
        if (status == MemberStatus.ACTIVE) {
            member.activate(NOW.minusHours(1));
        }
        return memberRepository.save(member);
    }

    private AuthSession issueSession(Member member, String rawSessionId) {
        return authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash(rawSessionId),
                NOW.plusHours(8),
                NOW.minusMinutes(1)));
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private static Cookie sessionCookie(String value) {
        return new Cookie(SessionCookieResolver.COOKIE_NAME, value);
    }

    private record Csrf(Cookie cookie) {
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
