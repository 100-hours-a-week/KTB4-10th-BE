package com.ktb10.kgb.guidebook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.JobType;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:generation-status-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class GenerationStatusApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
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
        LocalDateTime now = LocalDateTime.now(clock);
        member = memberRepository.save(Member.register(
                OauthProvider.KAKAO, UUID.randomUUID().toString(), "여행자", null, null, now));
        String rawSessionId = UUID.randomUUID().toString();
        authSessionRepository.saveAndFlush(AuthSession.issue(
                member, sessionIdHasher.hash(rawSessionId), now.plusHours(1), now));
        sessionCookie = new Cookie("KGB_SESSION", rawSessionId);
    }

    @ParameterizedTest
    @EnumSource(value = GenerationStatus.class, names = {"PENDING", "PROCESSING", "CANCELED"})
    void returnsOwnedJobStateWithoutChangingIt(GenerationStatus jobStatus) throws Exception {
        GenerationJob job = saveJob(jobStatus);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", job.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("generation_job_get_success"))
                .andExpect(jsonPath("$.data.job_id").value(job.getId()))
                .andExpect(jsonPath("$.data.job_type").value("INITIAL"))
                .andExpect(jsonPath("$.data.status").value(jobStatus.name()))
                .andExpect(jsonPath("$.data.attempt_count").value(0))
                .andExpect(jsonPath("$.data.guidebook_id").value((Object) null))
                .andExpect(jsonPath("$.data.guidebook_version").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.error").value((Object) null));

        assertThat(generationJobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(jobStatus);
    }

    @Test
    void returnsCompletedGuidebookWithoutVersion() throws Exception {
        GenerationJob job = saveJob(GenerationStatus.COMPLETED);
        Guidebook guidebook = saveGuidebook();
        ReflectionTestUtils.setField(job, "guidebookId", guidebook.getId());
        ReflectionTestUtils.setField(job, "attemptCount", (short) 3);
        generationJobRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", job.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.guidebook_id").value(guidebook.getId()))
                .andExpect(jsonPath("$.data.guidebook_version").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.attempt_count").value(3))
                .andExpect(jsonPath("$.data.error").value((Object) null));
    }

    @Test
    void failedJobDoesNotExposeInternalAiPayload() throws Exception {
        GenerationJob job = saveJob(GenerationStatus.FAILED);
        ReflectionTestUtils.setField(job, "errorPayload", "{\"message\":\"private-ai-secret\"}");
        generationJobRepository.flush();
        entityManager.clear();

        String response = mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", job.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error.code").value("GENERATION_FAILED"))
                .andExpect(jsonPath("$.data.error.message").value("가이드북 생성에 실패했습니다."))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-ai-secret", "error_payload", "request_payload");
    }

    @Test
    void regenerationRetainsTargetGuidebookBeforeCompletion() throws Exception {
        GenerationJob job = saveJob(GenerationStatus.PROCESSING);
        Guidebook guidebook = saveGuidebook();
        ReflectionTestUtils.setField(job, "jobType", JobType.REGENERATION);
        ReflectionTestUtils.setField(job, "guidebookId", guidebook.getId());
        generationJobRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", job.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job_type").value("REGENERATION"))
                .andExpect(jsonPath("$.data.guidebook_id").value(guidebook.getId()))
                .andExpect(jsonPath("$.data.guidebook_version").doesNotHaveJsonPath());
    }

    @Test
    void rejectsAnotherMembersJob() throws Exception {
        GenerationJob job = saveJob(GenerationStatus.PENDING);
        Member other = memberRepository.save(Member.register(
                OauthProvider.KAKAO, UUID.randomUUID().toString(), "다른회원", null, null,
                LocalDateTime.now(clock)));
        ReflectionTestUtils.setField(job, "member", other);
        generationJobRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", job.getId())
                        .cookie(sessionCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void returnsNotFoundForMissingJob() throws Exception {
        mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", Long.MAX_VALUE)
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/guidebook-generations/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidJobId(String jobId) throws Exception {
        mockMvc.perform(get("/api/v1/guidebook-generations/{jobId}", jobId)
                        .cookie(sessionCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    private GenerationJob saveJob(GenerationStatus status) {
        GenerationJob job = GenerationJob.createInitial(
                member, "{}", UUID.randomUUID().toString(), LocalDateTime.now(clock));
        // 상태 전이 구현은 #22 범위이며 여기서는 저장된 각 상태의 조회를 검증한다.
        ReflectionTestUtils.setField(job, "status", status);
        return generationJobRepository.saveAndFlush(job);
    }

    private Guidebook saveGuidebook() {
        Guidebook guidebook = Guidebook.create(
                "여행", 1L, LocalDate.now(clock), LocalDate.now(clock),
                Companion.ALONE, 1, null, LocalDateTime.now(clock));
        entityManager.persist(guidebook);
        entityManager.flush();
        return guidebook;
    }
}
