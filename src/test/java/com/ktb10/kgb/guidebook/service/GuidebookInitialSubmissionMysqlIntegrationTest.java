package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.event.GuidebookGenerationRequestedEvent;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
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
class GuidebookInitialSubmissionMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GuidebookAiClient guidebookAiClient;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @ParameterizedTest
    @EnumSource(value = AiFailureType.class, names = {"CONNECT_TIMEOUT", "UPSTREAM_5XX"})
    void initialSubmissionFailureMovesCommittedJobToFailed(AiFailureType failureType) {
        given(guidebookAiClient.requestGeneration(any())).willThrow(new AiClientException(
                "AI 접수 실패",
                new IllegalStateException("private upstream response"),
                failureType,
                "/guidebooks-generations",
                503,
                100L));

        Long jobId = new TransactionTemplate(transactionManager).execute(status -> {
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
            eventPublisher.publishEvent(new GuidebookGenerationRequestedEvent(job.getId()));
            return job.getId();
        });

        GenerationJob failedJob = generationJobRepository.findById(jobId).orElseThrow();
        assertThat(failedJob.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failedJob.getAiJobId()).isNull();
        assertThat(failedJob.getErrorPayload())
                .contains("AI_SUBMISSION_FAILED", failureType.code(), "retryable");
        assertThat(creditTransactionRepository.count()).isZero();
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
