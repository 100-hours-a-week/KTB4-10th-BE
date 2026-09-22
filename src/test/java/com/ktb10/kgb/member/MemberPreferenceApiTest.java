package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionCookieResolver;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-preference-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SESSION_COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
@Import(MemberPreferenceApiTest.FixedClockConfiguration.class)
class MemberPreferenceApiTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-09-22T03:00:00Z");
    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @BeforeEach
    void cleanUp() {
        memberPreferenceRepository.deleteAll();
        authSessionRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void onboardingMemberWithoutSelectionsGetsEmptyPreferences() throws Exception {
        Member member = saveMember("empty-preferences");
        issueSession(member, "empty-preferences-session");

        mockMvc.perform(get("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("empty-preferences-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("preference_get_success"))
                .andExpect(jsonPath("$.data.selections").isEmpty());
    }

    @Test
    void validPreferencesReplaceSelectionsAndActivateOnboardingMember() throws Exception {
        Member member = saveMember("save-preferences");
        issueSession(member, "save-preferences-session");
        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("save-preferences-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPreferenceRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("preference_update_success"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.selections.length()").value(3))
                .andExpect(jsonPath("$.data.selections[0].preference_type").value("THEME"))
                .andExpect(jsonPath("$.data.selections[0].preference_code").value("NATURE"))
                .andExpect(jsonPath("$.data.selections[1].preference_type").value("DETAIL"))
                .andExpect(jsonPath("$.data.selections[2].preference_type")
                        .value("TRAVEL_STYLE"));

        Member activated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(activated.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(member.getId()))
                .extracting(MemberPreference::getPreferenceCode)
                .containsExactlyInAnyOrder(
                        PreferenceCode.NATURE,
                        PreferenceCode.NATURE_MOUNTAIN,
                        PreferenceCode.RELAXING);

        mockMvc.perform(get("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("save-preferences-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selections[0].preference_code").value("NATURE"))
                .andExpect(jsonPath("$.data.selections[1].preference_code")
                        .value("NATURE_MOUNTAIN"))
                .andExpect(jsonPath("$.data.selections[2].preference_code").value("RELAXING"));
    }

    @Test
    void activeMemberCanReplaceEntirePreferenceSet() throws Exception {
        Member member = saveMember("replace-preferences");
        member.activate(NOW.minusHours(1));
        memberRepository.save(member);
        memberPreferenceRepository.saveAll(List.of(
                MemberPreference.select(member, PreferenceCode.NATURE),
                MemberPreference.select(member, PreferenceCode.NATURE_MOUNTAIN)));
        issueSession(member, "replace-preferences-session");
        Cookie csrfCookie = issueCsrfCookie();

        String request = "{\"selections\":["
                + "{\"preference_type\":\"THEME\",\"preference_code\":\"HISTORY\"},"
                + "{\"preference_type\":\"DETAIL\","
                + "\"preference_code\":\"HISTORY_RELIC\"}]}";
        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("replace-preferences-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(member.getId()))
                .extracting(MemberPreference::getPreferenceCode)
                .containsExactlyInAnyOrder(PreferenceCode.HISTORY, PreferenceCode.HISTORY_RELIC);
    }

    @Test
    void invalidSelectionCountsReturnPreferenceInvalid() throws Exception {
        Member member = saveMember("preference-count-validation");
        issueSession(member, "preference-count-validation-session");
        Cookie csrfCookie = issueCsrfCookie();

        List<String> invalidRequests = List.of(
                "{\"selections\":[]}",
                "{\"selections\":["
                        + "{\"preference_type\":\"THEME\","
                        + "\"preference_code\":\"NATURE\"}]}",
                "{\"selections\":["
                        + "{\"preference_type\":\"THEME\","
                        + "\"preference_code\":\"NATURE\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"NATURE_MOUNTAIN\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"NATURE_RIVER_SEA\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"NATURE_ECOLOGY\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"NATURE_PARK\"}]}",
                "{\"selections\":["
                        + "{\"preference_type\":\"THEME\","
                        + "\"preference_code\":\"NATURE\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"NATURE_MOUNTAIN\"},"
                        + "{\"preference_type\":\"THEME\","
                        + "\"preference_code\":\"HISTORY\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"HISTORY_RELIC\"},"
                        + "{\"preference_type\":\"THEME\","
                        + "\"preference_code\":\"ATTRACTION\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"ATTRACTION_LANDMARK\"},"
                        + "{\"preference_type\":\"THEME\","
                        + "\"preference_code\":\"EXPERIENCE\"},"
                        + "{\"preference_type\":\"DETAIL\","
                        + "\"preference_code\":\"EXPERIENCE_CRAFT\"}]}");

        for (String request : invalidRequests) {
            mockMvc.perform(put("/api/v1/members/me/preferences")
                            .cookie(
                                    sessionCookie("preference-count-validation-session"),
                                    csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("PREFERENCE_INVALID"));
        }
    }

    @Test
    void invalidPreferenceKeepsExistingSelectionsAndOnboardingStatus() throws Exception {
        Member member = saveMember("invalid-preferences");
        memberPreferenceRepository.save(MemberPreference.select(member, PreferenceCode.NATURE));
        issueSession(member, "invalid-preferences-session");
        Cookie csrfCookie = issueCsrfCookie();

        String invalidRequest = "{\"selections\":["
                + "{\"preference_type\":\"THEME\",\"preference_code\":\"HISTORY\"},"
                + "{\"preference_type\":\"DETAIL\","
                + "\"preference_code\":\"NATURE_MOUNTAIN\"}]}";
        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("invalid-preferences-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PREFERENCE_INVALID"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ONBOARDING);
        assertThat(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(member.getId()))
                .extracting(MemberPreference::getPreferenceCode)
                .containsExactly(PreferenceCode.NATURE);
    }

    @Test
    void unknownCodeAndDuplicateSelectionReturnPreferenceInvalid() throws Exception {
        Member member = saveMember("preference-validation");
        issueSession(member, "preference-validation-session");
        Cookie csrfCookie = issueCsrfCookie();

        String unknownCode = "{\"selections\":["
                + "{\"preference_type\":\"THEME\",\"preference_code\":\"UNKNOWN\"}]}";
        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("preference-validation-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownCode))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PREFERENCE_INVALID"));

        String duplicate = "{\"selections\":["
                + "{\"preference_type\":\"THEME\",\"preference_code\":\"NATURE\"},"
                + "{\"preference_type\":\"THEME\",\"preference_code\":\"NATURE\"},"
                + "{\"preference_type\":\"DETAIL\","
                + "\"preference_code\":\"NATURE_MOUNTAIN\"}]}";
        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("preference-validation-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicate))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PREFERENCE_INVALID"));
    }

    @Test
    void malformedRequestReturnsBadRequest() throws Exception {
        Member member = saveMember("malformed-preferences");
        issueSession(member, "malformed-preferences-session");
        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("malformed-preferences-session"), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void preferencesRequireAuthenticationAndUpdateRequiresCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));

        Member member = saveMember("csrf-preferences");
        issueSession(member, "csrf-preferences-session");
        mockMvc.perform(put("/api/v1/members/me/preferences")
                        .cookie(sessionCookie("csrf-preferences-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPreferenceRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    private Member saveMember(String subject) {
        return memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                subject,
                "여행자",
                null,
                null,
                NOW.minusDays(1)));
    }

    private AuthSession issueSession(Member member, String rawSessionId) {
        return authSessionRepository.saveAndFlush(AuthSession.issue(
                member,
                sessionIdHasher.hash(rawSessionId),
                NOW.plusHours(8),
                NOW.minusMinutes(1)));
    }

    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
    }

    private static Cookie sessionCookie(String value) {
        return new Cookie(SessionCookieResolver.COOKIE_NAME, value);
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
