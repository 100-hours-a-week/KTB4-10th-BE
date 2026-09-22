package com.ktb10.kgb.guidebook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guidebook-delete-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class GuidebookDeleteApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 3, 0);

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
    private SessionIdHasher sessionIdHasher;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Member member;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        member = saveActiveMember("delete-member");
        sessionCookie = issueSession(member);
    }

    @Test
    void softDeletesOnlyRequestingMembersRelationship() throws Exception {
        Member otherMember = saveActiveMember("other-member");
        Guidebook guidebook = saveGuidebook();
        MemberGuidebook relationship = memberGuidebookRepository.saveAndFlush(
                MemberGuidebook.create(member, guidebook, AcquisitionType.CREATED, NOW));
        MemberGuidebook otherRelationship = memberGuidebookRepository.saveAndFlush(
                MemberGuidebook.create(otherMember, guidebook, AcquisitionType.IMPORTED, NOW));
        entityManager.clear();

        mockMvc.perform(delete("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        MemberGuidebook deleted = memberGuidebookRepository.findById(relationship.getId())
                .orElseThrow();
        MemberGuidebook retained = memberGuidebookRepository.findById(otherRelationship.getId())
                .orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(retained.getDeletedAt()).isNull();
        assertThat(guidebookRepository.findById(guidebook.getId())).isPresent();

        mockMvc.perform(get("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenDeletingAgain() throws Exception {
        Guidebook guidebook = saveGuidebook();
        memberGuidebookRepository.saveAndFlush(
                MemberGuidebook.create(member, guidebook, AcquisitionType.CREATED, NOW));

        mockMvc.perform(delete("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundForAnotherMembersGuidebook() throws Exception {
        Member otherMember = saveActiveMember("owners-member");
        Guidebook guidebook = saveGuidebook();
        memberGuidebookRepository.saveAndFlush(
                MemberGuidebook.create(otherMember, guidebook, AcquisitionType.CREATED, NOW));

        mockMvc.perform(delete("/api/v1/guidebooks/{guidebookId}", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundForMissingGuidebook() throws Exception {
        mockMvc.perform(delete("/api/v1/guidebooks/{guidebookId}", Long.MAX_VALUE)
                        .with(csrf())
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/guidebooks/1").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidGuidebookId(String guidebookId) throws Exception {
        mockMvc.perform(delete("/api/v1/guidebooks/{guidebookId}", guidebookId)
                        .with(csrf())
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
