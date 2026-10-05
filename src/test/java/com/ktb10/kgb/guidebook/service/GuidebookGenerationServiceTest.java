package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.AiClientException;
import com.ktb10.kgb.guidebook.client.AiFailureType;
import com.ktb10.kgb.guidebook.client.AiIntegrationErrorLogger;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import com.ktb10.kgb.guidebook.event.GuidebookGenerationRequestedEvent;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "initial-request-1";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-19T03:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private GenerationJobRepository generationJobRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPreferenceRepository memberPreferenceRepository;

    @Mock
    private CreditWalletRepository creditWalletRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectProvider<GuidebookAiClient> guidebookAiClientProvider;

    @Mock
    private GuidebookResultService guidebookResultService;

    @Mock
    private AiIntegrationErrorLogger aiIntegrationErrorLogger;

    private ObjectMapper objectMapper;
    private GuidebookGenerationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new GuidebookGenerationService(
                generationJobRepository,
                memberRepository,
                memberPreferenceRepository,
                creditWalletRepository,
                eventPublisher,
                guidebookAiClientProvider,
                guidebookResultService,
                aiIntegrationErrorLogger,
                objectMapper,
                CLOCK);
    }

    @Test
    void createsPendingInitialJob() {
        GuidebookGenerationRequest request = validRequest();
        Member member = member();
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(generationJobRepository.existsByMemberIdAndStatusIn(
                any(Long.class), org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .willReturn(false);
        given(creditWalletRepository.findByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(walletWithBalance(member, 1)));
        given(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(MEMBER_ID))
                .willReturn(preferences(member));
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));
        given(generationJobRepository.save(any(GenerationJob.class)))
                .willAnswer(invocation -> {
                    GenerationJob job = invocation.getArgument(0);
                    ReflectionTestUtils.setField(job, "id", 301L);
                    return job;
                });

        var response = service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request);

        assertThat(response.jobType().name()).isEqualTo("INITIAL");
        assertThat(response.status()).isEqualTo(GenerationStatus.PENDING);
        assertThat(response.guidebookId()).isNull();
        verify(generationJobRepository).save(any(GenerationJob.class));
        verify(eventPublisher).publishEvent(new GuidebookGenerationRequestedEvent(301L));
    }

    @Test
    void getsLatestAiStatusBeforeReturningJobStatus() {
        Member member = member();
        GenerationJob job = GenerationJob.createInitial(
                member, "{}", IDEMPOTENCY_KEY, LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(job, "id", 301L);
        job.registerAiJob("ai-job-301", LocalDateTime.now(CLOCK));
        GuidebookAiClient aiClient = org.mockito.Mockito.mock(GuidebookAiClient.class);
        AiGenerationStatusResponse aiResponse =
                AiGenerationStatusResponse.processing("ai-job-301");
        given(generationJobRepository.existsById(301L)).willReturn(true);
        given(generationJobRepository.existsByIdAndMemberId(301L, MEMBER_ID)).willReturn(true);
        given(generationJobRepository.findById(301L)).willReturn(Optional.of(job));
        given(guidebookAiClientProvider.getIfAvailable()).willReturn(aiClient);
        given(aiClient.getGenerationStatus("ai-job-301")).willReturn(aiResponse);

        service.getGenerationJobStatus(MEMBER_ID, 301L);

        verify(aiClient).getGenerationStatus("ai-job-301");
        verify(guidebookResultService).apply(301L, aiResponse);
    }

    @Test
    void marksJobFailedWhenAiStatusCannotBeFetched() {
        Member member = member();
        GenerationJob job = GenerationJob.createInitial(
                member, "{}", IDEMPOTENCY_KEY, LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(job, "id", 301L);
        job.registerAiJob("ai-job-301", LocalDateTime.now(CLOCK));
        GuidebookAiClient aiClient = org.mockito.Mockito.mock(GuidebookAiClient.class);
        given(generationJobRepository.existsById(301L)).willReturn(true);
        given(generationJobRepository.existsByIdAndMemberId(301L, MEMBER_ID)).willReturn(true);
        given(generationJobRepository.findById(301L)).willReturn(Optional.of(job));
        given(guidebookAiClientProvider.getIfAvailable()).willReturn(aiClient);
        given(aiClient.getGenerationStatus("ai-job-301"))
                .willThrow(new AiClientException(
                        "AI 서버 상태 조회 요청에 실패했습니다.",
                        new IllegalStateException("connection reset"),
                        AiFailureType.CONNECTION_ERROR,
                        "/guidebooks-generations/{jobId}",
                        null,
                        20L));

        var response = service.getGenerationJobStatus(MEMBER_ID, 301L);

        assertThat(response.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(response.error().code()).isEqualTo("GENERATION_FAILED");
        assertThat(job.getErrorPayload()).contains("AI_STATUS_UNAVAILABLE");
        verify(guidebookResultService, never()).apply(any(), any());
        verify(aiIntegrationErrorLogger).logFailure(
                org.mockito.ArgumentMatchers.eq("ai_generation_status_sync_failed"),
                org.mockito.ArgumentMatchers.eq(301L),
                org.mockito.ArgumentMatchers.eq("ai-job-301"),
                org.mockito.ArgumentMatchers.any(AiClientException.class));
    }

    @Test
    void storesCurrentMemberPreferencesInRequestPayload() throws Exception {
        GuidebookGenerationRequest request = validRequest();
        Member member = member();
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(generationJobRepository.existsByMemberIdAndStatusIn(
                any(Long.class), org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .willReturn(false);
        given(creditWalletRepository.findByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(walletWithBalance(member, 1)));
        given(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(MEMBER_ID))
                .willReturn(preferences(member));
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));
        given(generationJobRepository.save(any(GenerationJob.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request);

        var jobCaptor = org.mockito.ArgumentCaptor.forClass(GenerationJob.class);
        verify(generationJobRepository).save(jobCaptor.capture());
        InitialGenerationRequestPayload payload = objectMapper.readValue(
                jobCaptor.getValue().getRequestPayload(),
                InitialGenerationRequestPayload.class);
        assertThat(payload.request()).isEqualTo(request);
        assertThat(payload.preferences())
                .extracting(InitialGenerationRequestPayload.PreferenceSnapshot::preferenceCode)
                .containsExactly("NATURE", "NATURE_MOUNTAIN", "RELAXING");
    }

    @Test
    void returnsExistingJobForSameIdempotentRequest() throws Exception {
        GuidebookGenerationRequest request = validRequest();
        GenerationJob existingJob = GenerationJob.createInitial(
                member(),
                requestPayload(request),
                IDEMPOTENCY_KEY,
                LocalDate.of(2026, 9, 19).atStartOfDay());
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.of(existingJob));

        var response = service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request);

        assertThat(response.status()).isEqualTo(GenerationStatus.PENDING);
        verify(generationJobRepository, never()).save(any());
    }

    @Test
    void rejectsDifferentRequestUsingSameIdempotencyKey() throws Exception {
        GenerationJob existingJob = GenerationJob.createInitial(
                member(),
                requestPayload(validRequest()),
                IDEMPOTENCY_KEY,
                LocalDate.of(2026, 9, 19).atStartOfDay());
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.of(existingJob));
        GuidebookGenerationRequest differentRequest = new GuidebookGenerationRequest(
                "서울특별시",
                "종로구",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2);

        assertThatThrownBy(() -> service.createInitial(
                MEMBER_ID, IDEMPOTENCY_KEY, differentRequest))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsRequestWhenAnotherGenerationIsActive() {
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member()));
        given(generationJobRepository.existsByMemberIdAndStatusIn(
                any(Long.class), org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .willReturn(true);

        assertThatThrownBy(() -> service.createInitial(
                MEMBER_ID, IDEMPOTENCY_KEY, validRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.GENERATION_IN_PROGRESS));
    }

    @Test
    void rejectsGenerationWhenMemberWasWithdrawnBeforeMemberLock() {
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createInitial(
                MEMBER_ID, IDEMPOTENCY_KEY, validRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                com.ktb10.kgb.common.error.CommonErrorCode
                                        .AUTH_SESSION_REQUIRED));
        verify(generationJobRepository, never()).save(any());
    }

    @Test
    void rejectsGenerationWhenMemberIsNotActive() {
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(onboardingMember()));

        assertThatThrownBy(() -> service.createInitial(
                MEMBER_ID, IDEMPOTENCY_KEY, validRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                com.ktb10.kgb.common.error.CommonErrorCode
                                        .AUTH_SESSION_REQUIRED));
        verify(generationJobRepository, never()).save(any());
    }

    @Test
    void rejectsRequestWhenCreditBalanceIsInsufficient() {
        Member member = member();
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));
        given(generationJobRepository.existsByMemberIdAndStatusIn(
                any(Long.class), org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .willReturn(false);
        given(creditWalletRepository.findByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(CreditWallet.open(member, LocalDateTime.now(CLOCK))));

        assertThatThrownBy(() -> service.createInitial(
                MEMBER_ID, IDEMPOTENCY_KEY, validRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.CREDIT_INSUFFICIENT));

        verify(memberPreferenceRepository, never())
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(any());
        verify(generationJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsTripLongerThanSevenDays() {
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "경상북도",
                "경주시",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 8),
                Companion.FRIEND,
                2);
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.GUIDEBOOK_INVALID_PERIOD));
    }

    @Test
    void rejectsPeopleCountThatDoesNotMatchCompanion() {
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "경상북도",
                "경주시",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.ALONE,
                2);
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.GUIDEBOOK_INVALID_PARTY));
    }

    @Test
    void rejectsCityThatDoesNotBelongToProvince() {
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "경상북도",
                "종로구",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2);
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.GUIDEBOOK_INVALID_REGION));
    }

    @Test
    void acceptsCityWithoutItsNestedDistrictForProvince() {
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "경기도",
                "수원시",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2);
        Member member = member();
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(generationJobRepository.existsByMemberIdAndStatusIn(
                any(Long.class), org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .willReturn(false);
        given(creditWalletRepository.findByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(walletWithBalance(member, 1)));
        given(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(MEMBER_ID))
                .willReturn(preferences(member));
        given(memberRepository.findActiveByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));
        given(generationJobRepository.save(any(GenerationJob.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request).status())
                .isEqualTo(GenerationStatus.PENDING);
    }

    @Test
    void rejectsManualRetryForNonRetryableSubmissionFailure() {
        GenerationJob job = failedJob(
                "{\"code\":\"AI_SUBMISSION_FAILED\","
                        + "\"failure_type\":\"upstream_4xx\",\"retryable\":false}");
        given(generationJobRepository.existsById(301L)).willReturn(true);
        given(generationJobRepository.existsByIdAndMemberId(301L, MEMBER_ID)).willReturn(true);
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        assertThatThrownBy(() -> service.retry(MEMBER_ID, 301L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.RETRY_INVALID_STATE));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repeatedManualRetryPublishesOnlyOneNewAttempt() {
        GenerationJob job = failedJob(
                "{\"code\":\"AI_SUBMISSION_FAILED\","
                        + "\"failure_type\":\"upstream_5xx\",\"retryable\":true}");
        given(generationJobRepository.existsById(301L)).willReturn(true);
        given(generationJobRepository.existsByIdAndMemberId(301L, MEMBER_ID)).willReturn(true);
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        service.retry(MEMBER_ID, 301L);

        assertThatThrownBy(() -> service.retry(MEMBER_ID, 301L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(GuidebookErrorCode.RETRY_INVALID_STATE));
        verify(eventPublisher).publishEvent(new GuidebookGenerationRequestedEvent(301L));
        assertThat(job.getAttemptCount()).isEqualTo((short) 1);
    }

    private GuidebookGenerationRequest validRequest() {
        return new GuidebookGenerationRequest(
                "경상북도",
                "경주시",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2);
    }

    private String requestPayload(GuidebookGenerationRequest request) throws Exception {
        Member member = member();
        List<PreferenceSnapshot> preferenceSnapshots = preferences(member).stream()
                .map(preference -> new PreferenceSnapshot(
                        preference.getPreferenceType().name(),
                        preference.getPreferenceCode().name()))
                .toList();
        return objectMapper.writeValueAsString(new InitialGenerationRequestPayload(
                request,
                preferenceSnapshots));
    }

    private List<MemberPreference> preferences(Member member) {
        return List.of(
                MemberPreference.select(member, PreferenceCode.NATURE),
                MemberPreference.select(member, PreferenceCode.NATURE_MOUNTAIN),
                MemberPreference.select(member, PreferenceCode.RELAXING));
    }

    private Member member() {
        Member member = onboardingMember();
        member.activate(LocalDate.of(2026, 9, 19).atStartOfDay());
        return member;
    }

    private Member onboardingMember() {
        return Member.register(
                OauthProvider.KAKAO,
                "guidebook-service-member",
                "여행자",
                null,
                null,
                LocalDate.of(2026, 9, 19).atStartOfDay());
    }

    private CreditWallet walletWithBalance(Member member, int balance) {
        CreditWallet wallet = CreditWallet.open(member, LocalDateTime.now(CLOCK));
        wallet.grant(balance, LocalDateTime.now(CLOCK));
        return wallet;
    }

    private GenerationJob failedJob(String errorPayload) {
        GenerationJob job = GenerationJob.createInitial(
                member(), "{}", IDEMPOTENCY_KEY, LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(job, "id", 301L);
        job.fail(errorPayload, LocalDateTime.now(CLOCK));
        return job;
    }
}
