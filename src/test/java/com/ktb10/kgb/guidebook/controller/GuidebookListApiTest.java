package com.ktb10.kgb.guidebook.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guidebook-list-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class GuidebookListApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 3, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private GuidebookRepository guidebookRepository;
    @Autowired
    private MemberGuidebookRepository memberGuidebookRepository;
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
        member = saveActiveMember("list-member");
        sessionCookie = issueSession(member);
    }

    @Test
    void returnsActiveGuidebooksInStoredOrder() throws Exception {
        Guidebook older = saveGuidebook("오래된 여행");
        Guidebook newer = saveGuidebook("최근 여행");
        memberGuidebookRepository.save(MemberGuidebook.create(
                member, older, AcquisitionType.CREATED, NOW.minusDays(1)));
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                member, newer, AcquisitionType.IMPORTED, NOW));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebooks").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("guidebook_list_success"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].guidebook_id").value(newer.getId()))
                .andExpect(jsonPath("$.data.items[0].title").value("최근 여행"))
                .andExpect(jsonPath("$.data.items[0].region").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.items[0].content_html").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.items[1].guidebook_id").value(older.getId()))
                .andExpect(jsonPath("$.data.next_cursor").doesNotExist())
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void returnsEmptyListWhenMemberHasNoGuidebooks() throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").doesNotExist())
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void excludesDeletedAndOtherMembersGuidebooks() throws Exception {
        Member otherMember = saveActiveMember("other-list-member");
        Guidebook deleted = saveGuidebook("삭제된 여행");
        Guidebook other = saveGuidebook("다른 회원 여행");
        MemberGuidebook deletedRelationship = MemberGuidebook.create(
                member, deleted, AcquisitionType.CREATED, NOW);
        ReflectionTestUtils.setField(deletedRelationship, "deletedAt", NOW.plusHours(1));
        memberGuidebookRepository.save(deletedRelationship);
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                otherMember, other, AcquisitionType.CREATED, NOW));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebooks").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void continuesWithCompositeCursorWhenCreatedAtIsSame() throws Exception {
        Guidebook first = saveGuidebook("첫 번째 여행");
        Guidebook second = saveGuidebook("두 번째 여행");
        memberGuidebookRepository.save(MemberGuidebook.create(
                member, first, AcquisitionType.CREATED, NOW));
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                member, second, AcquisitionType.CREATED, NOW));
        entityManager.clear();

        MvcResult firstPage = mockMvc.perform(get("/api/v1/guidebooks")
                        .param("size", "1")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].guidebook_id").value(second.getId()))
                .andExpect(jsonPath("$.data.has_more").value(true))
                .andReturn();
        JsonNode response = objectMapper.readTree(firstPage.getResponse().getContentAsByteArray());
        String cursor = response.at("/data/next_cursor").asText();

        mockMvc.perform(get("/api/v1/guidebooks")
                        .param("size", "1")
                        .param("cursor", cursor)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].guidebook_id").value(first.getId()))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void rejectsInvalidCursor() throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks")
                        .param("cursor", "invalid-cursor")
                        .cookie(sessionCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "abc"})
    void rejectsInvalidSize(String size) throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks")
                        .param("size", size)
                        .cookie(sessionCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/guidebooks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
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

    private Guidebook saveGuidebook(String title) {
        return guidebookRepository.saveAndFlush(Guidebook.create(
                title,
                47L,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2,
                "<article>여행 안내</article>",
                NOW));
    }
}
