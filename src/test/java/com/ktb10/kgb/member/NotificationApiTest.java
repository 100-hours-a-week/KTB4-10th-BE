package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionCookieResolver;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import com.ktb10.kgb.member.service.NotificationService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:notification-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
@Import(NotificationApiTest.FixedClockConfiguration.class)
class NotificationApiTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-25T03:00:00Z");
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

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void returnsOwnedNotificationsNewestFirstWithOneBasedPageMetadata() throws Exception {
        Member member = saveActiveMember("notification-list-member");
        issueSession(member, "notification-list-session");
        Notification oldest = notification(member, "첫 번째", "101", NOW.minusMinutes(3));
        Notification middle = notification(member, "두 번째", "102", NOW.minusMinutes(2));
        Notification newest = notification(member, "세 번째", "103", NOW.minusMinutes(1));
        notificationRepository.saveAllAndFlush(List.of(oldest, middle, newest));

        mockMvc.perform(get("/api/v1/notifications")
                        .cookie(sessionCookie("notification-list-session"))
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("notification_list_success"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].notification_id")
                        .value(newest.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].title").value("세 번째"))
                .andExpect(jsonPath("$.data.items[0].type").value("GUIDEBOOK_COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].reference_type").value("GUIDEBOOK"))
                .andExpect(jsonPath("$.data.items[0].reference_id").value("103"))
                .andExpect(jsonPath("$.data.items[0].created_at")
                        .value("2026-09-25T02:59:00Z"))
                .andExpect(jsonPath("$.data.items[1].notification_id")
                        .value(middle.getId().toString()))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total_items").value(3))
                .andExpect(jsonPath("$.data.total_pages").value(2))
                .andExpect(jsonPath("$.data.unread_count").value(3));
    }

    @Test
    void returnsEmptyItemsForPageOutsideAvailableRange() throws Exception {
        Member member = saveActiveMember("notification-empty-page-member");
        issueSession(member, "notification-empty-page-session");

        mockMvc.perform(get("/api/v1/notifications")
                        .cookie(sessionCookie("notification-empty-page-session"))
                        .queryParam("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.size").value(4))
                .andExpect(jsonPath("$.data.total_items").value(0))
                .andExpect(jsonPath("$.data.total_pages").value(0))
                .andExpect(jsonPath("$.data.unread_count").value(0));
    }

    @Test
    void rejectsInvalidPaginationAndUnauthenticatedRequest() throws Exception {
        Member member = saveActiveMember("notification-invalid-page-member");
        issueSession(member, "notification-invalid-page-session");

        mockMvc.perform(get("/api/v1/notifications")
                        .cookie(sessionCookie("notification-invalid-page-session"))
                        .queryParam("page", "0")
                        .queryParam("size", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @Test
    void deletesOnlyOwnedNotificationAndTreatsMissingTargetAsSuccess() throws Exception {
        Member owner = saveActiveMember("notification-delete-owner");
        Member other = saveActiveMember("notification-delete-other");
        issueSession(owner, "notification-delete-session");
        Notification owned = notification(owner, "내 알림", "201", NOW.minusMinutes(2));
        Notification others = notification(other, "타인 알림", "202", NOW.minusMinutes(1));
        notificationRepository.saveAllAndFlush(List.of(owned, others));
        Csrf csrf = csrf();

        performDelete(owned.getId(), "notification-delete-session", csrf)
                .andExpect(status().isNoContent());
        performDelete(owned.getId(), "notification-delete-session", csrf)
                .andExpect(status().isNoContent());
        performDelete(others.getId(), "notification-delete-session", csrf)
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.existsById(owned.getId())).isFalse();
        assertThat(notificationRepository.existsById(others.getId())).isTrue();
    }

    @Test
    void deletesCurrentSnapshotAndRequiresCsrf() throws Exception {
        Member member = saveActiveMember("notification-delete-all-member");
        issueSession(member, "notification-delete-all-session");
        notificationRepository.saveAllAndFlush(List.of(
                notification(member, "첫 번째", "301", NOW.minusMinutes(2)),
                notification(member, "두 번째", "302", NOW.minusMinutes(1))));

        mockMvc.perform(delete("/api/v1/notifications")
                        .cookie(sessionCookie("notification-delete-all-session")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));

        Csrf csrf = csrf();
        mockMvc.perform(delete("/api/v1/notifications")
                        .cookie(sessionCookie("notification-delete-all-session"), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.countByRecipientId(member.getId())).isZero();
    }

    @Test
    void creationKeepsOnlyTwentyNewestNotifications() {
        Member member = saveActiveMember("notification-limit-member");

        for (int sequence = 1; sequence <= 21; sequence++) {
            notificationService.create(
                    member.getId(),
                    NotificationType.GUIDEBOOK_COMPLETED,
                    "가이드북 완성 " + sequence,
                    "가이드북을 확인해 주세요.",
                    NotificationReferenceType.GUIDEBOOK,
                    Integer.toString(sequence));
        }

        assertThat(notificationRepository.countByRecipientId(member.getId())).isEqualTo(20);
        assertThat(notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        member.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent())
                .extracting(Notification::getReferenceId)
                .contains("21")
                .doesNotContain("1");
    }

    private org.springframework.test.web.servlet.ResultActions performDelete(
            Long notificationId,
            String sessionId,
            Csrf csrf) throws Exception {
        return mockMvc.perform(delete("/api/v1/notifications/{notificationId}", notificationId)
                .cookie(sessionCookie(sessionId), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.cookie().getValue()));
    }

    private Member saveActiveMember(String oauthSubject) {
        Member member = Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
        member.activate(NOW.minusHours(1));
        return memberRepository.saveAndFlush(member);
    }

    private AuthSession issueSession(Member member, String rawSessionId) {
        return authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash(rawSessionId),
                NOW.plusHours(8),
                NOW.minusMinutes(1)));
    }

    private static Notification notification(
            Member recipient,
            String title,
            String referenceId,
            LocalDateTime createdAt) {
        return Notification.create(
                recipient,
                NotificationType.GUIDEBOOK_COMPLETED,
                title,
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                referenceId,
                createdAt);
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
