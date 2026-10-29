package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.ErrorCode;
import com.ktb10.kgb.guidebook.callback.GenerationCallbackSubmissionListener;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookRegenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.JobType;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.entity.Region;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.guidebook.repository.RegionRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class GuidebookRegenerationConcurrencyMysqlIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 2;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private GuidebookGenerationService generationService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;
    @Autowired
    private GuidebookRepository guidebookRepository;
    @Autowired
    private MemberGuidebookRepository memberGuidebookRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Clock clock;

    @MockitoBean
    private GenerationCallbackSubmissionListener callbackSubmissionListener;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void concurrentLastRegenerationAcceptsOnlyOneJob() throws Exception {
        Fixture fixture = new TransactionTemplate(transactionManager)
                .execute(status -> createFixtureWithOneRemainingRegeneration());

        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RegenerationAttempt> first = executor.submit(() -> attemptRegeneration(
                    fixture, "concurrent-regeneration-1", ready, start));
            Future<RegenerationAttempt> second = executor.submit(() -> attemptRegeneration(
                    fixture, "concurrent-regeneration-2", ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            RegenerationAttempt.success(),
                            RegenerationAttempt.failure(
                                    GuidebookErrorCode.GENERATION_IN_PROGRESS));
        }

        List<GenerationJob> regenerationJobs = generationJobRepository.findAll().stream()
                .filter(job -> job.getJobType() == JobType.REGENERATION)
                .toList();
        assertThat(regenerationJobs).singleElement().satisfies(job -> {
            assertThat(job.getMember().getId()).isEqualTo(fixture.memberId());
            assertThat(job.getGuidebookId()).isEqualTo(fixture.guidebookId());
            assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        });
        assertThat(guidebookRepository.findById(fixture.guidebookId()).orElseThrow()
                .getVersion()).isEqualTo(3);
    }

    private RegenerationAttempt attemptRegeneration(
            Fixture fixture,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 재생성 시작 신호를 기다리는 시간이 초과되었습니다.");
        }
        try {
            generationService.regenerate(
                    fixture.memberId(),
                    fixture.guidebookId(),
                    idempotencyKey,
                    new GuidebookRegenerationRequest("마지막 재생성을 요청합니다."));
            return RegenerationAttempt.success();
        } catch (BusinessException exception) {
            return RegenerationAttempt.failure(exception.errorCode());
        }
    }

    private Fixture createFixtureWithOneRemainingRegeneration() {
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = Member.register(
                OauthProvider.KAKAO,
                UUID.randomUUID().toString(),
                "동시 재생성 회원",
                null,
                null,
                now.minusDays(1));
        member.activate(now.minusDays(1));
        memberRepository.save(member);
        memberPreferenceRepository.saveAll(List.of(
                MemberPreference.select(member, PreferenceCode.NATURE),
                MemberPreference.select(member, PreferenceCode.NATURE_MOUNTAIN)));

        Region region = regionRepository.save(Region.create(
                "concurrent-" + UUID.randomUUID().toString().substring(0, 8),
                "경상북도"));
        Guidebook guidebook = Guidebook.create(
                "동시 재생성 여행",
                region.getId(),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2),
                Companion.FRIEND,
                2,
                "<article>최초 결과</article>",
                now.minusMinutes(1));
        guidebook.replaceGeneratedContent("<article>첫 번째 재생성</article>", now);
        guidebook.replaceGeneratedContent("<article>두 번째 재생성</article>", now);
        guidebookRepository.save(guidebook);
        memberGuidebookRepository.save(MemberGuidebook.create(
                member, guidebook, AcquisitionType.CREATED, now.minusMinutes(1)));

        GenerationJob initialJob = GenerationJob.createInitial(
                member,
                initialRequestPayload(),
                "initial-" + UUID.randomUUID(),
                now.minusMinutes(2));
        initialJob.registerAiJob("ai-initial-" + UUID.randomUUID(), now.minusMinutes(1));
        initialJob.complete(guidebook.getId(), now.minusMinutes(1));
        generationJobRepository.save(initialJob);
        generationJobRepository.flush();
        return new Fixture(member.getId(), guidebook.getId());
    }

    private String initialRequestPayload() {
        try {
            return objectMapper.writeValueAsString(new InitialGenerationRequestPayload(
                    new GuidebookGenerationRequest(
                            "경상북도",
                            "경주시",
                            LocalDate.of(2026, 11, 1),
                            LocalDate.of(2026, 11, 2),
                            Companion.FRIEND,
                            2),
                    List.of(
                            new PreferenceSnapshot("THEME", "NATURE"),
                            new PreferenceSnapshot("DETAIL", "NATURE_MOUNTAIN"))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("테스트 요청을 직렬화할 수 없습니다.", exception);
        }
    }

    private record Fixture(Long memberId, Long guidebookId) {
    }

    private record RegenerationAttempt(boolean accepted, String errorCode) {

        private static RegenerationAttempt success() {
            return new RegenerationAttempt(true, null);
        }

        private static RegenerationAttempt failure(ErrorCode errorCode) {
            return new RegenerationAttempt(false, errorCode.code());
        }
    }
}
