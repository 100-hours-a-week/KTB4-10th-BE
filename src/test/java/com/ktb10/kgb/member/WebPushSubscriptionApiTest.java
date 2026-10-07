package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionCookieResolver;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:web-push-subscription-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SESSION_COOKIE_SECURE=false",
        "webpush.vapid.public-key=" + WebPushSubscriptionApiTest.P256DH
})
@AutoConfigureMockMvc
@Import(WebPushSubscriptionApiTest.FixedClockConfiguration.class)
class WebPushSubscriptionApiTest {

    static final String P256DH =
            "BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String AUTH = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String ENDPOINT = "https://push.example.test/subscriptions/browser-1";
    private static final Instant CURRENT_INSTANT = Instant.parse("2026-10-07T06:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);
    private static final long EXPIRATION_TIME = 1_830_816_000_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private WebPushSubscriptionRepository subscriptionRepository;

    @BeforeEach
    void cleanUp() {
        subscriptionRepository.deleteAll();
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void activeMemberCanGetVapidPublicKey() throws Exception {
        Member member = activeMember("vapid-public-key-member");
        issueSession(member, "vapid-public-key-session");

        mockMvc.perform(get("/api/v1/push/vapid-public-key")
                        .cookie(sessionCookie("vapid-public-key-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("push_vapid_public_key_get_success"))
                .andExpect(jsonPath("$.data.public_key").value(P256DH));
    }

    @Test
    void registerAndRenewAreIdempotentForSameEndpoint() throws Exception {
        Member member = activeMember("push-upsert-member");
        issueSession(member, "push-upsert-session");
        Csrf csrf = csrf();

        MvcResult first = register("push-upsert-session", csrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("web_push_subscription_upsert_success"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        Long subscriptionId = jsonLong(first, "subscription_id");

        register("push-upsert-session", csrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription_id").value(subscriptionId));

        assertThat(subscriptionRepository.findAll()).singleElement().satisfies(subscription -> {
            assertThat(subscription.getId()).isEqualTo(subscriptionId);
            assertThat(subscription.getMember().getId()).isEqualTo(member.getId());
            assertThat(subscription.getStatus()).isEqualTo(WebPushSubscriptionStatus.ACTIVE);
            assertThat(subscription.getEndpoint()).isEqualTo(ENDPOINT);
        });
    }

    @Test
    void memberCanRegisterMultipleBrowserSubscriptions() throws Exception {
        Member member = activeMember("push-multiple-browser-member");
        issueSession(member, "push-multiple-browser-session");
        Csrf csrf = csrf();

        register("push-multiple-browser-session", csrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk());
        register(
                        "push-multiple-browser-session",
                        csrf,
                        "https://push.example.test/subscriptions/browser-2",
                        P256DH,
                        AUTH)
                .andExpect(status().isOk());

        assertThat(subscriptionRepository.countByMemberIdAndStatus(
                        member.getId(), WebPushSubscriptionStatus.ACTIVE))
                .isEqualTo(2);
    }

    @Test
    void activeEndpointOwnedByAnotherMemberIsRejected() throws Exception {
        Member owner = activeMember("push-owner");
        issueSession(owner, "push-owner-session");
        Csrf ownerCsrf = csrf();
        register("push-owner-session", ownerCsrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk());

        Member other = activeMember("push-other");
        issueSession(other, "push-other-session");
        Csrf otherCsrf = csrf();

        register("push-other-session", otherCsrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("WEB_PUSH_SUBSCRIPTION_CONFLICT"));
    }

    @Test
    void memberCanRevokeOnlyOwnedSubscription() throws Exception {
        Member owner = activeMember("push-delete-owner");
        issueSession(owner, "push-delete-owner-session");
        Csrf ownerCsrf = csrf();
        MvcResult registered = register(
                "push-delete-owner-session", ownerCsrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk())
                .andReturn();
        Long subscriptionId = jsonLong(registered, "subscription_id");

        Member other = activeMember("push-delete-other");
        issueSession(other, "push-delete-other-session");
        Csrf otherCsrf = csrf();
        mockMvc.perform(delete("/api/v1/members/me/push-subscriptions/{subscriptionId}",
                        subscriptionId)
                        .cookie(sessionCookie("push-delete-other-session"), otherCsrf.cookie())
                        .header("X-XSRF-TOKEN", otherCsrf.cookie().getValue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("WEB_PUSH_SUBSCRIPTION_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/members/me/push-subscriptions/{subscriptionId}",
                        subscriptionId)
                        .cookie(sessionCookie("push-delete-owner-session"), ownerCsrf.cookie())
                        .header("X-XSRF-TOKEN", ownerCsrf.cookie().getValue()))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findById(subscriptionId).orElseThrow().getStatus())
                .isEqualTo(WebPushSubscriptionStatus.REVOKED);
    }

    @Test
    void logoutRevokesCurrentSessionSubscriptionWithoutChangingPushSetting() throws Exception {
        Member member = activeMember("push-logout-member");
        issueSession(member, "push-logout-session");
        Csrf csrf = csrf();
        MvcResult registered = register("push-logout-session", csrf, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk())
                .andReturn();
        Long subscriptionId = jsonLong(registered, "subscription_id");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessionCookie("push-logout-session"), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isNoContent());

        WebPushSubscription revoked = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(WebPushSubscriptionStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isEqualTo(NOW);
        assertThat(memberRepository.findById(member.getId()).orElseThrow().isPushEnabled()).isTrue();
    }

    @Test
    void registrationValidatesAuthenticationCsrfEndpointAndKeys() throws Exception {
        mockMvc.perform(get("/api/v1/push/vapid-public-key"))
                .andExpect(status().isUnauthorized());

        Member member = activeMember("push-validation-member");
        issueSession(member, "push-validation-session");
        mockMvc.perform(put("/api/v1/members/me/push-subscriptions")
                        .cookie(sessionCookie("push-validation-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(ENDPOINT, P256DH, AUTH)))
                .andExpect(status().isForbidden());

        Csrf csrf = csrf();
        register("push-validation-session", csrf, "http://push.example.test/a", P256DH, AUTH)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("WEB_PUSH_SUBSCRIPTION_INVALID"));
        register("push-validation-session", csrf, ENDPOINT, "invalid", AUTH)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("WEB_PUSH_SUBSCRIPTION_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String rawSessionId,
            Csrf csrf,
            String endpoint,
            String p256dh,
            String auth) throws Exception {
        return mockMvc.perform(put("/api/v1/members/me/push-subscriptions")
                .cookie(sessionCookie(rawSessionId), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(endpoint, p256dh, auth)));
    }

    private String requestBody(String endpoint, String p256dh, String auth) {
        return """
                {
                  "endpoint": "%s",
                  "expiration_time": %d,
                  "keys": {
                    "p256dh": "%s",
                    "auth": "%s"
                  }
                }
                """.formatted(endpoint, EXPIRATION_TIME, p256dh, auth);
    }

    private Long jsonLong(MvcResult result, String field) throws Exception {
        Number value = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data." + field);
        return value.longValue();
    }

    private Member activeMember(String oauthSubject) {
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
