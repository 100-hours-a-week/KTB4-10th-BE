package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRegenerationRequest;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookRegenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.RegenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.guidebook.service.GuidebookResultService;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GenerationCallbackTransactionServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private GenerationJobRepository jobs;
    @Mock
    private GuidebookResultService results;
    @Mock
    private AiGuidebookRequestMapper mapper;
    @Mock
    private ApplicationEventPublisher events;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private GenerationCallbackTransactionService transactions;

    @BeforeEach
    void setUp() {
        transactions = new GenerationCallbackTransactionService(
                jobs, results, mapper, json, events,
                new GenerationCallbackSettings("test-token", Duration.ofMinutes(15)), CLOCK);
    }

    @Test
    void submitsInitialRequestAndRegistersAcceptedJob() throws Exception {
        GenerationJob job = initialJob();
        var request = initialAiRequest(job);
        var prepared = transactions.prepare(301L);
        transactions.registerAccepted(prepared,
                new AiGenerationAcceptedResponse("ai-initial", AiGenerationStatus.PENDING));

        assertThat(prepared.initialRequest()).isSameAs(request);
        assertThat(prepared.deadline()).isEqualTo(CLOCK.instant().plusSeconds(900));
        assertThat(prepared.requestId()).isEqualTo("301-0");
        assertThat(job.getAiJobId()).isEqualTo("ai-initial");
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        verifyNoInteractions(results);
    }

    @Test
    void submitsRegenerationUsingSourceAiJobAndCurrentRequestId() throws Exception {
        var payload = new RegenerationRequestPayload(
                new GuidebookRegenerationRequest("걷기를 줄여 주세요."), input(), "ai-source");
        GenerationJob job = GenerationJob.createRegeneration(
                member(), 50L, json.writeValueAsString(payload), "regen-key", LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(job, "id", 301L);
        when(jobs.findByIdForUpdate(301L)).thenReturn(Optional.of(job));
        var request = new AiGuidebookRegenerationRequest("301-0", "걷기를 줄여 주세요.");
        var prepared = transactions.prepare(301L);
        transactions.registerAccepted(prepared,
                new AiGenerationAcceptedResponse("ai-regenerated", AiGenerationStatus.PROCESSING));

        assertThat(prepared.sourceAiJobId()).isEqualTo("ai-source");
        assertThat(prepared.regenerationRequest()).isEqualTo(request);
        assertThat(job.getAiJobId()).isEqualTo("ai-regenerated");
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
        verifyNoInteractions(mapper, results);
    }

    @Test
    void doesNotPrepareTheSameAttemptTwiceBeforeAiIdIsRegistered() throws Exception {
        GenerationJob job = initialJob();
        initialAiRequest(job);
        assertThat(transactions.prepare(301L)).isNotNull();
        assertThat(transactions.prepare(301L)).isNull();
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.getAttemptStartedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(job.getAiJobId()).isNull();
        verifyNoInteractions(events, results);
    }

    @Test
    void doesNotResubmitAnAlreadyAcceptedJob() throws Exception {
        GenerationJob job = initialJob();
        job.registerAiJob("ai-existing", LocalDateTime.now(CLOCK));

        assertThat(transactions.prepare(301L)).isNull();

        verifyNoInteractions(mapper, results);
    }

    private GenerationJob initialJob() throws Exception {
        GenerationJob job = GenerationJob.createInitial(
                member(), json.writeValueAsString(input()), "initial-key", LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(job, "id", 301L);
        when(jobs.findByIdForUpdate(301L)).thenReturn(Optional.of(job));
        return job;
    }

    private AiGuidebookRequest initialAiRequest(GenerationJob job) {
        var request = org.mockito.Mockito.mock(AiGuidebookRequest.class);
        when(mapper.map(any(InitialGenerationRequestPayload.class), eq(job.getId()), eq(0)))
                .thenReturn(request);
        return request;
    }

    private InitialGenerationRequestPayload input() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        return new InitialGenerationRequestPayload(new GuidebookGenerationRequest(
                "서울특별시", "강남구", date, date, Companion.ALONE, 1), List.of());
    }

    private Member member() {
        return Member.register(OauthProvider.KAKAO, "callback-test", "테스트", null, null,
                LocalDateTime.now(CLOCK));
    }
}
