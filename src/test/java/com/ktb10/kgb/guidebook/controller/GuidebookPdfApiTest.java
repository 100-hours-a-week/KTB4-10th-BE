package com.ktb10.kgb.guidebook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.pdf.GuidebookPdfConverter;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guidebook-pdf-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class GuidebookPdfApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 3, 0);
    private static final byte[] PDF = "%PDF-1.7\nresult".getBytes();

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
    private Clock clock;
    @MockitoBean
    private GuidebookPdfConverter guidebookPdfConverter;

    private Member member;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        member = saveMember("pdf-member", true);
        sessionCookie = issueSession(member);
        when(guidebookPdfConverter.convert(anyLong(), anyString())).thenReturn(PDF);
    }

    @Test
    void downloadsOwnedGuidebookAsPdf() throws Exception {
        Guidebook guidebook = saveGuidebook("<html><body>경주 여행</body></html>");
        memberGuidebookRepository.saveAndFlush(
                MemberGuidebook.create(member, guidebook, AcquisitionType.CREATED, NOW));

        byte[] response = mockMvc.perform(post(
                        "/api/v1/guidebooks/{guidebookId}/exports", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie)
                        .contentType("application/json")
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"guidebook-" + guidebook.getId()
                                + ".pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(response).isEqualTo(PDF);
        verify(guidebookPdfConverter).convert(
                guidebook.getId(), "<html><body>경주 여행</body></html>");
    }

    @Test
    void hidesAnotherMembersGuidebook() throws Exception {
        Member otherMember = saveMember("other-pdf-member", true);
        Guidebook guidebook = saveGuidebook("<html>다른 회원 가이드북</html>");
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                otherMember, guidebook, AcquisitionType.CREATED, NOW));

        mockMvc.perform(post("/api/v1/guidebooks/{guidebookId}/exports", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie)
                        .contentType("application/json")
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        verify(guidebookPdfConverter, never()).convert(anyLong(), anyString());
    }

    @Test
    void rejectsGuidebookWithoutHtml() throws Exception {
        Guidebook guidebook = saveGuidebook(null);
        memberGuidebookRepository.saveAndFlush(
                MemberGuidebook.create(member, guidebook, AcquisitionType.CREATED, NOW));

        mockMvc.perform(post("/api/v1/guidebooks/{guidebookId}/exports", guidebook.getId())
                        .with(csrf())
                        .cookie(sessionCookie)
                        .contentType("application/json")
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GUIDEBOOK_NOT_READY"));

        verify(guidebookPdfConverter, never()).convert(anyLong(), anyString());
    }

    @Test
    void requiresActiveMember() throws Exception {
        Member onboardingMember = saveMember("onboarding-pdf-member", false);
        Cookie onboardingSession = issueSession(onboardingMember);

        mockMvc.perform(post("/api/v1/guidebooks/1/exports")
                        .with(csrf())
                        .cookie(onboardingSession)
                        .contentType("application/json")
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void rejectsMissingFormat() throws Exception {
        mockMvc.perform(post("/api/v1/guidebooks/1/exports")
                        .with(csrf())
                        .cookie(sessionCookie)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/guidebooks/1/exports")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    private Member saveMember(String oauthSubject, boolean active) {
        Member savedMember = Member.register(
                OauthProvider.KAKAO, oauthSubject, "여행자", null, null, NOW);
        if (active) {
            savedMember.activate(NOW);
        }
        return memberRepository.saveAndFlush(savedMember);
    }

    private Cookie issueSession(Member sessionMember) {
        String rawSessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        authSessionRepository.saveAndFlush(AuthSession.issue(
                sessionMember, sessionIdHasher.hash(rawSessionId), now.plusHours(1), now));
        return new Cookie("KGB_SESSION", rawSessionId);
    }

    private Guidebook saveGuidebook(String contentHtml) {
        return guidebookRepository.saveAndFlush(Guidebook.create(
                "경주 여행",
                47L,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2,
                contentHtml,
                NOW));
    }
}
