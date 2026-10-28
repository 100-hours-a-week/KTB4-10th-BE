package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:generation-callback;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "ai.generation.delivery-mode=callback",
        "ai.generation.callback-token=callback-test-token",
        "ai.generation.timeout=15m",
        "ai.generation.redis.enabled=false",
        "kgb.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class GenerationCallbackIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final String PATH = "/internal/v1/ai/generation-events";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private GenerationJobRepository jobs;
    @Autowired
    private MemberRepository members;
    @Autowired
    private GenerationCallbackTransactionService transactions;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private GuidebookAiClient ai;
    @MockitoBean
    private com.ktb10.kgb.guidebook.client.AiGuidebookContentQuery contentQuery;
    @MockitoBean
    private com.ktb10.kgb.guidebook.client.TourismContentSnapshotQuery snapshots;
    @MockitoBean
    private com.ktb10.kgb.guidebook.repository.RegionRepository regions;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private com.ktb10.kgb.credit.repository.CreditWalletRepository wallets;
    @Autowired
    private com.ktb10.kgb.guidebook.repository.GuidebookRepository guidebooks;

    private GenerationJob job;
    private Member member;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        member = Member.register(OauthProvider.KAKAO, UUID.randomUUID().toString(),
                "콜백 테스트", null, null, now);
        member.activate(now);
        member = members.saveAndFlush(member);
        job = GenerationJob.createInitial(member, "{}", UUID.randomUUID().toString(), now);
        job.beginCallbackAttempt(now);
        job = jobs.saveAndFlush(job);
    }

    @Test
    void acceptsWithoutBrowserCsrfAndCommitsBeforeAcknowledgement() throws Exception {
        send(failed(2)).andExpect(status().isNoContent());

        GenerationJob stored = jobs.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(stored.getAiJobId()).isEqualTo(aiJobId());
        assertThat(stored.getErrorPayload()).contains("generation_failed");
    }

    @Test
    void completedCallbackStoresResultAndConsumesCreditOnlyOnce() throws Exception {
        var date = java.time.LocalDate.of(2026, 11, 1);
        var request = new com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest(
                "서울특별시", "종로구", date, date,
                com.ktb10.kgb.guidebook.entity.Companion.ALONE, 1);
        var input = new com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload(
                request, java.util.List.of());
        // H2 JSON은 JDBC 문자열을 다시 따옴표로 감싸므로 테스트 입력은 JSON으로 명시합니다.
        jdbc.update("UPDATE generation_jobs SET request_payload = ? FORMAT JSON WHERE id = ?",
                json.writeValueAsString(input), job.getId());
        var now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        var wallet = com.ktb10.kgb.credit.entity.CreditWallet.open(member, now);
        wallet.grant(2, now);
        wallets.saveAndFlush(wallet);
        var region = new org.springframework.transaction.support.TransactionTemplate(
                transactionManager).execute(transaction -> {
                    var saved = com.ktb10.kgb.guidebook.entity.Region.create(
                            "T" + job.getId(), "서울특별시");
                    entityManager.persist(saved);
                    return saved;
                });
        when(regions.findProvinceByName("서울특별시")).thenReturn(java.util.Optional.of(region));
        var candidate = new com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Content(
                "126508", com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.ContentType.PLACE,
                "테스트 장소", "소개", "HS", "HS01", "HS010100", "서울 종로구",
                new com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Coordinates(37.5, 127.0),
                null, null);
        when(contentQuery.findAll("서울특별시", "종로구", date, date))
                .thenReturn(java.util.List.of(candidate));
        when(snapshots.findBySourceContentId("126508")).thenReturn(java.util.Optional.of(
                new com.ktb10.kgb.guidebook.client.TourismContentSnapshotQuery.TourismContentSnapshot(
                        701L, "126508", "테스트 장소", "HS", "서울 종로구",
                        37.5, 127.0, null, null, null)));
        var preparedSubmission = transactions.prepare(job.getId());
        assertThat(preparedSubmission.deadline()).isEqualTo(NOW.plusSeconds(900));
        assertThat(preparedSubmission.initialRequest().requestId()).isEqualTo(job.getId() + "-0");
        var result = new com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.GuidebookResult(
                "서울 여행", "요약", null, "<article>여행</article>", java.util.List.of(
                        new com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.ItineraryDay(
                                1, date, "일차 요약", java.util.List.of(
                                        new com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Place(
                                                1, java.time.LocalTime.of(9, 0),
                                                "126508", 60, "추천 이유")))));
        var completed = new GenerationCallbackEnvelope("guidebook_completed",
                new GenerationCallbackEnvelope.Data(UUID.randomUUID(), job.getId() + "-0",
                        3, aiJobId(),
                        com.ktb10.kgb.guidebook.client.AiGenerationStatus.COMPLETED,
                        null, result, null));
        long before = guidebooks.count();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Void> deliver = () -> {
                start.await();
                send(completed).andExpect(status().isNoContent());
                return null;
            };
            var first = executor.submit(deliver);
            var second = executor.submit(deliver);
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        transactions.registerAccepted(new GenerationCallbackTransactionService.Prepared(
                job.getId(), 0, NOW.plusSeconds(900), null, null, null),
                new com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse(
                        aiJobId(), com.ktb10.kgb.guidebook.client.AiGenerationStatus.PENDING));
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.COMPLETED);
        assertThat(guidebooks.count()).isEqualTo(before + 1);
        assertThat(wallets.findByMemberId(member.getId()).orElseThrow().getCreditBalance()).isEqualTo(1);
        when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        transactions.expire(job.getId(), 0);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.COMPLETED);
    }

    @Test
    void rejectsMissingOrWrongBearerEvenWithBrowserAuthentication() throws Exception {
        String payload = json.writeValueAsString(failed(1));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization", "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(authentication(principal(member.getId())))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.PENDING);
    }

    @Test
    void rejectsInconsistentEnvelopeAndInvalidSequence() throws Exception {
        var invalid = new GenerationCallbackEnvelope("guidebook_completed",
                failed(1).data());
        send(invalid).andExpect(status().isBadRequest());
        send(failed(0)).andExpect(status().isBadRequest());
    }

    @Test
    void bindsAiJobBeforeAcceptedResponseAndIgnoresOutOfOrderProgress() throws Exception {
        send(progress(2, "writing")).andExpect(status().isNoContent());
        send(progress(1, "arranging")).andExpect(status().isNoContent());
        GenerationJob stored = jobs.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
        assertThat(stored.getAiJobId()).isEqualTo(aiJobId());
        transactions.registerAccepted(new GenerationCallbackTransactionService.Prepared(
                job.getId(), 0, NOW.plusSeconds(900), null, null, null),
                new com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse(
                        aiJobId(), com.ktb10.kgb.guidebook.client.AiGenerationStatus.PROCESSING));
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.PROCESSING);
    }

    @Test
    void ignoresRepeatedTerminalAndLateProgress() throws Exception {
        var callback = failed(3);
        send(callback).andExpect(status().isNoContent());
        send(callback).andExpect(status().isNoContent());
        send(progress(4, "writing")).andExpect(status().isNoContent());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void previousAttemptCannotAffectRetriedJob() throws Exception {
        send(failed(2)).andExpect(status().isNoContent());
        GenerationJob retry = jobs.findById(job.getId()).orElseThrow();
        retry.retry(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        retry.beginCallbackAttempt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        jobs.saveAndFlush(retry);

        send(progress(3, "writing")).andExpect(status().isNoContent());
        when(clock.instant()).thenReturn(NOW.plusSeconds(601));
        transactions.expire(job.getId(), 0);
        GenerationJob stored = jobs.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(stored.getAttemptCount()).isEqualTo((short) 1);
        assertThat(stored.getAiJobId()).isNull();
    }

    @Test
    void callbackAtFifteenMinutesTimesOutEvenBeforeTimerRuns() throws Exception {
        when(clock.instant()).thenReturn(NOW.plusSeconds(900));
        send(failed(2)).andExpect(status().isNoContent());
        var payload = json.readTree(jobs.findById(job.getId()).orElseThrow().getErrorPayload());
        if (payload.isTextual()) {
            payload = json.readTree(payload.asText());
        }
        assertThat(payload.path("code").asText()).isEqualTo("GENERATION_TIMEOUT");
        assertThat(payload.path("retryable").asBoolean()).isTrue();
    }

    @Test
    void timeoutDoesNotRunEarlyAndProgressDoesNotExtendDeadline() throws Exception {
        when(clock.instant()).thenReturn(NOW.plusSeconds(899));
        send(progress(1, "arranging")).andExpect(status().isNoContent());
        transactions.expire(job.getId(), 0);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.PROCESSING);
        when(clock.instant()).thenReturn(NOW.plusSeconds(900));
        transactions.expire(job.getId(), 0);
        assertThat(jobs.findById(job.getId()).orElseThrow().getErrorPayload())
                .contains("GENERATION_TIMEOUT");
    }

    @Test
    void statusGetDoesNotCallAiInCallbackMode() throws Exception {
        mvc.perform(get("/api/v1/guidebook-generations/" + job.getId())
                        .with(authentication(principal(member.getId()))))
                .andExpect(status().isOk());
        verifyNoInteractions(ai);
    }

    @Test
    void streamRejectsAnotherMembersJob() throws Exception {
        mvc.perform(get("/api/v1/guidebook-generations/" + job.getId() + "/stream")
                        .with(authentication(principal(member.getId() + 1000))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerStreamReceivesLatestProgressAndDatabaseStatusWithoutPollingAi() throws Exception {
        send(progress(2, "writing")).andExpect(status().isNoContent());
        var result = mvc.perform(get("/api/v1/guidebook-generations/" + job.getId() + "/stream")
                        .with(authentication(principal(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .request().asyncStarted()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("event:generation-status", "\"step\":\"writing\"", "\"sequence\":2");
        verifyNoInteractions(ai);
        send(failed(3)).andExpect(status().isNoContent());
    }

    @Test
    void rejectsDifferentAiJobForSameAttempt() throws Exception {
        send(progress(1, "arranging")).andExpect(status().isNoContent());
        var original = failed(2).data();
        var wrong = new GenerationCallbackEnvelope("guidebook_failed",
                new GenerationCallbackEnvelope.Data(original.eventId(), original.requestId(),
                        original.sequence(), "different-ai", original.status(),
                        null, null, original.error()));
        send(wrong).andExpect(status().isConflict());
    }

    private UsernamePasswordAuthenticationToken principal(Long memberId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(memberId, MemberStatus.ACTIVE, "a".repeat(64)),
                null, java.util.List.of());
    }

    private org.springframework.test.web.servlet.ResultActions send(
            GenerationCallbackEnvelope envelope) throws Exception {
        return mvc.perform(post(PATH).header("Authorization", "Bearer callback-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(envelope)));
    }

    private GenerationCallbackEnvelope failed(long sequence) {
        return json.convertValue(Map.of(
                "message", "guidebook_failed",
                "data", Map.of("event_id", UUID.randomUUID().toString(),
                        "request_id", job.getId() + "-0", "sequence", sequence,
                        "job_id", aiJobId(), "status", "failed",
                        "error", Map.of("code", "generation_failed", "message", "생성 실패"))),
                GenerationCallbackEnvelope.class);
    }

    private GenerationCallbackEnvelope progress(long sequence, String step) {
        return json.convertValue(Map.of(
                "message", "guidebook_processing",
                "data", Map.of("event_id", UUID.randomUUID().toString(),
                        "request_id", job.getId() + "-0", "sequence", sequence,
                        "job_id", aiJobId(), "status", "processing",
                        "progress", Map.of("step", step, "message", "진행 중"))),
                GenerationCallbackEnvelope.class);
    }

    private String aiJobId() {
        return "ai-test-" + job.getId();
    }
}
