package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.guidebook.callback.GenerationCallbackTransactionService;
import com.ktb10.kgb.guidebook.callback.GenerationCallbackEnvelope;
import com.ktb10.kgb.guidebook.callback.GenerationTimerScheduler;
import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.GenerationError;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.callback.GenerationCallbackSubmissionService;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class GuidebookInitialSubmissionMysqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private GenerationCallbackSubmissionService submissions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GuidebookAiClient guidebookAiClient;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private GenerationTimerScheduler scheduler;

    @Autowired
    private GenerationCallbackTransactionService callbacks;

    @Autowired
    private GuidebookGenerationService generations;
    @Autowired
    private CreditWalletRepository wallets;
    @Autowired
    private MemberPreferenceRepository preferences;
    @Autowired
    private HikariDataSource pool;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        given(clock.withZone(any())).willReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        // 고정된 과거 Clock의 기한을 실제 시스템 시계로 즉시 실행하지 않는다.
        given(scheduler.schedule(any(), any())).willReturn((ScheduledFuture) mock(ScheduledFuture.class));
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
    }

    @Test
    void initialRequestReleasesConnectionBeforeAiAndAcceptsCallbackBefore202() throws Exception {
        CompletableFuture<Void> callbackApplied = new CompletableFuture<>();
        Long memberId = new TransactionTemplate(transactionManager).execute(status -> {
            Member member = memberRepository.save(Member.register(OauthProvider.KAKAO,
                    UUID.randomUUID().toString(), "여행자", null, null, LocalDateTime.now(clock)));
            member.activate(LocalDateTime.now(clock));
            CreditWallet wallet = CreditWallet.open(member, LocalDateTime.now(clock));
            wallet.grant(1, LocalDateTime.now(clock));
            wallets.save(wallet);
            preferences.save(MemberPreference.select(member, PreferenceCode.NATURE));
            return member.getId();
        });
        given(guidebookAiClient.requestGeneration(any())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.hasResource(pool)).isFalse();
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            var request = (com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest) invocation.getArgument(0);
            long jobId = Long.parseLong(request.requestId().split("-")[0]);
            assertThat(generationJobRepository.findById(jobId).orElseThrow().getAttemptStartedAt()).isNotNull();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.submit(() -> callbacks.receive(failedCallback(request.requestId())))
                        .get(3, TimeUnit.SECONDS);
            }
            callbackApplied.complete(null);
            return new AiGenerationAcceptedResponse("ai-fast", AiGenerationStatus.PENDING);
        });
        LocalDate date = LocalDate.of(2026, 11, 1);

        var response = generations.createInitial(memberId, UUID.randomUUID().toString(),
                new GuidebookGenerationRequest("경상북도", "경주시", date, date, Companion.FRIEND, 2));

        assertThat(response.status()).isEqualTo(GenerationStatus.PENDING);
        callbackApplied.get(5, TimeUnit.SECONDS);
        GenerationJob job = generationJobRepository.findById(response.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.getAiJobId()).isEqualTo("ai-fast");
        assertThat(wallets.findByMemberId(memberId).orElseThrow().getCreditBalance()).isEqualTo(1);
    }

    @Test
    void concurrentSubmissionDoesNotDispatchAgainWhileFirstHttpIsWaiting() throws Exception {
        Long jobId = persistJob();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(guidebookAiClient.requestGeneration(any())).willAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("AI 접수 응답 대기 시간 초과");
            }
            return new AiGenerationAcceptedResponse("ai-once", AiGenerationStatus.PENDING);
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> submissions.submit(jobId));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                executor.submit(() -> submissions.submit(jobId)).get(3, TimeUnit.SECONDS);
                assertThat(first.isDone()).isFalse();
                verify(guidebookAiClient, times(1)).requestGeneration(any());
            } finally {
                release.countDown();
            }
            first.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void timeoutDuringHttpIsNotOverwrittenByLate202() {
        Long jobId = persistJob();
        given(guidebookAiClient.requestGeneration(any())).willAnswer(invocation -> {
            given(clock.instant()).willReturn(NOW.plusSeconds(900));
            callbacks.expire(jobId, 0);
            return new AiGenerationAcceptedResponse("ai-late", AiGenerationStatus.PROCESSING);
        });

        submissions.submit(jobId);

        GenerationJob job = generationJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.getErrorPayload()).contains("GENERATION_TIMEOUT");
        assertThat(job.getAiJobId()).isNull();
    }

    @Test
    void rejectsOuterTransactionsInsteadOfSuspendingAndRetainingTheirConnections() {
        Long jobId = persistJob();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> submissions.submit(jobId)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> generations.retry(1L, jobId)))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(guidebookAiClient);
    }

    private GenerationCallbackEnvelope failedCallback(String requestId) {
        return new GenerationCallbackEnvelope("guidebook_failed", new GenerationCallbackEnvelope.Data(
                UUID.randomUUID(), requestId, 3, "ai-fast", AiGenerationStatus.FAILED,
                null, null, new GenerationError("generation_failed", "생성 실패")));
    }

    @ParameterizedTest
    @EnumSource(value = AiFailureType.class, names = {"CONNECT_TIMEOUT", "UPSTREAM_5XX"})
    void uncertainSubmissionWaitsForCallbackAndFailsOnlyAtDeadline(AiFailureType failureType) {
        given(guidebookAiClient.requestGeneration(any())).willThrow(new AiClientException(
                "AI 접수 실패",
                new IllegalStateException("private upstream response"),
                failureType,
                "/guidebooks-generations",
                null,
                100L));

        Long jobId = persistJob();

        submissions.submit(jobId);

        GenerationJob pendingJob = generationJobRepository.findById(jobId).orElseThrow();
        assertThat(pendingJob.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(pendingJob.getAiJobId()).isNull();
        assertThat(pendingJob.getErrorPayload()).isNull();
        assertThat(pendingJob.getAttemptStartedAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        callbacks.expire(jobId, 0);
        assertThat(generationJobRepository.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.PENDING);

        given(clock.instant()).willReturn(NOW.plusSeconds(900));
        callbacks.expire(jobId, 0);
        GenerationJob failedJob = generationJobRepository.findById(jobId).orElseThrow();
        assertThat(failedJob.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failedJob.getAiJobId()).isNull();
        assertThat(failedJob.getErrorPayload())
                .contains("GENERATION_TIMEOUT", "retryable");
        assertThat(creditTransactionRepository.count()).isZero();
    }

    private Long persistJob() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member member = memberRepository.save(Member.register(
                    OauthProvider.KAKAO,
                    UUID.randomUUID().toString(),
                    "여행자",
                    null,
                    null,
                    LocalDateTime.now()));
            GenerationJob job = generationJobRepository.save(GenerationJob.createInitial(
                    member,
                    requestPayload(),
                    UUID.randomUUID().toString(),
                    LocalDateTime.now()));
            generationJobRepository.flush();
            return job.getId();
        });
    }

    private String requestPayload() {
        try {
            return objectMapper.writeValueAsString(new InitialGenerationRequestPayload(
                    new GuidebookGenerationRequest(
                            "경상북도",
                            "경주시",
                            LocalDate.of(2026, 10, 12),
                            LocalDate.of(2026, 10, 14),
                            Companion.FRIEND,
                            2),
                    List.of(
                            new PreferenceSnapshot("THEME", "NATURE"),
                            new PreferenceSnapshot("DETAIL", "NATURE_MOUNTAIN"))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("테스트 요청을 만들 수 없습니다.", exception);
        }
    }
}
