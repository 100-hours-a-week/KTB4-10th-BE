package com.ktb10.kgb.guidebook.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guidebook-detail-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class GuidebookDetailApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 3, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private GuidebookRepository guidebookRepository;
    @Autowired
    private MemberGuidebookRepository memberGuidebookRepository;
    @Autowired
    private ItineraryDayRepository itineraryDayRepository;
    @Autowired
    private ItineraryItemRepository itineraryItemRepository;
    @Autowired
    private SessionIdHasher sessionIdHasher;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Member member;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        member = saveActiveMember("detail-member");
        sessionCookie = issueSession(member);
    }

    @Test
    void returnsDetailForActivelyStoredGuidebook() throws Exception {
        Guidebook guidebook = saveGuidebook();
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                member, guidebook, AcquisitionType.CREATED, NOW));
        ItineraryDay firstDay = itineraryDayRepository.save(ItineraryDay.create(
                guidebook, 1, LocalDate.of(2026, 10, 12)));
        itineraryItemRepository.save(ItineraryItem.create(
                firstDay, 101L, 1, LocalTime.of(10, 0),
                "{\"title\":\"첨성대\"}", NOW));
        itineraryItemRepository.saveAndFlush(ItineraryItem.create(
                firstDay, 102L, 2, LocalTime.of(13, 0),
                "{\"title\":\"교촌마을\"}", NOW));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("guidebook_get_success"))
                .andExpect(jsonPath("$.data.guidebook_id").value(guidebook.getId()))
                .andExpect(jsonPath("$.data.title").value("경주 여행"))
                .andExpect(jsonPath("$.data.region_id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.region").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.start_date").value("2026-10-12"))
                .andExpect(jsonPath("$.data.end_date").value("2026-10-14"))
                .andExpect(jsonPath("$.data.people_count").value(2))
                .andExpect(jsonPath("$.data.companion").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.version").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.updated_at").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.content_html").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.itinerary[0].day_number").value(1))
                .andExpect(jsonPath("$.data.itinerary[0].itinerary_date")
                        .value("2026-10-12"))
                .andExpect(jsonPath("$.data.itinerary[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.itinerary[0].items[0].item_id").isNumber())
                .andExpect(jsonPath("$.data.itinerary[0].items[0].content_id").value(101))
                .andExpect(jsonPath("$.data.itinerary[0].items[0].sequence").value(1))
                .andExpect(jsonPath("$.data.itinerary[0].items[0].scheduled_time")
                        .value("10:00:00"))
                .andExpect(jsonPath("$.data.itinerary[0].items[0].place_snapshot.title")
                        .value("첨성대"))
                .andExpect(jsonPath("$.data.itinerary[0].items[1].place_snapshot.title")
                        .value("교촌마을"));
    }

    @Test
    void returnsNotFoundWhenStorageRelationshipIsSoftDeleted() throws Exception {
        Guidebook guidebook = saveGuidebook();
        MemberGuidebook relationship = MemberGuidebook.create(
                member, guidebook, AcquisitionType.CREATED, NOW);
        ReflectionTestUtils.setField(relationship, "deletedAt", NOW.plusHours(1));
        memberGuidebookRepository.saveAndFlush(relationship);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundForAnotherMembersGuidebook() throws Exception {
        Member otherMember = saveActiveMember("other-member");
        Guidebook guidebook = saveGuidebook();
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                otherMember, guidebook, AcquisitionType.CREATED, NOW));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundForMissingGuidebook() throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks/{guidebookId}", Long.MAX_VALUE)
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidGuidebookId(String guidebookId) throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks/{guidebookId}", guidebookId)
                        .cookie(sessionCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    private Member saveActiveMember(String oauthSubject) {
        Member savedMember = Member.register(
                OauthProvider.KAKAO, oauthSubject, "여행자", null, null, NOW);
        savedMember.activate(NOW);
        return memberRepository.saveAndFlush(savedMember);
    }

    private Cookie issueSession(Member sessionMember) {
        String rawSessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        authSessionRepository.saveAndFlush(AuthSession.issue(
                sessionMember, sessionIdHasher.hash(rawSessionId), now.plusHours(1), now));
        return new Cookie("KGB_SESSION", rawSessionId);
    }

    private Guidebook saveGuidebook() {
        return guidebookRepository.saveAndFlush(Guidebook.create(
                "경주 여행",
                47L,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2,
                "<article>여행 안내</article>",
                NOW));
    }
}
