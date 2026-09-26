package com.ktb10.kgb.guidebook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.dto.response.GenerationStatusResponse;
import com.ktb10.kgb.guidebook.dto.response.GuidebookGenerationResponse;
import com.ktb10.kgb.guidebook.entity.AdministrativeDistrict;
import com.ktb10.kgb.guidebook.entity.AdministrativeProvince;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import com.ktb10.kgb.guidebook.event.GuidebookGenerationRequestedEvent;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가이드북 생성 요청 접수와 회원 본인의 생성 작업 조회를 처리합니다. */
@Service
public class GuidebookGenerationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_TRIP_DAYS = 7;
    private static final Set<GenerationStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);

    private final GenerationJobRepository generationJobRepository;
    private final MemberRepository memberRepository;
    private final MemberPreferenceRepository memberPreferenceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<GuidebookAiClient> guidebookAiClientProvider;
    private final GuidebookResultService guidebookResultService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GuidebookGenerationService(
            GenerationJobRepository generationJobRepository,
            MemberRepository memberRepository,
            MemberPreferenceRepository memberPreferenceRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<GuidebookAiClient> guidebookAiClientProvider,
            GuidebookResultService guidebookResultService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.generationJobRepository = generationJobRepository;
        this.memberRepository = memberRepository;
        this.memberPreferenceRepository = memberPreferenceRepository;
        this.eventPublisher = eventPublisher;
        this.guidebookAiClientProvider = guidebookAiClientProvider;
        this.guidebookResultService = guidebookResultService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public GuidebookGenerationResponse createInitial(
            Long memberId,
            String idempotencyKey,
            GuidebookGenerationRequest request) {
        Optional<GenerationJob> existingJob =
                generationJobRepository.findByMemberIdAndIdempotencyKey(
                        memberId,
                        idempotencyKey);
        if (existingJob.isPresent()) {
            return handleRepeatedRequest(existingJob.get(), request);
        }

        validateTravelCondition(request);

        // TODO: #42 회원 행을 잠근 뒤 ACTIVE 상태를 재검증하고 현재 취향을 조회한다.
        // TODO: #42 멱등 비교용 요청과 취향 스냅샷을 구분해 requestPayload에 저장한다.
        // TODO: #42 생성권 지갑을 잠근 뒤 잔액을 확인한다.
        if (generationJobRepository.existsByMemberIdAndStatusIn(memberId, ACTIVE_STATUSES)) {
            throw new BusinessException(GuidebookErrorCode.GENERATION_IN_PROGRESS);
        }

        List<MemberPreference> memberPreferences = memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(memberId);
        if (memberPreferences.isEmpty()) {
            throw new BusinessException(GuidebookErrorCode.PREFERENCE_INVALID);
        }
        InitialGenerationRequestPayload payload = createInitialRequestPayload(
                request,
                memberPreferences);
        String requestPayload = serialize(payload);

        Member member = memberRepository.getReferenceById(memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        GenerationJob job = GenerationJob.createInitial(
                member,
                requestPayload,
                idempotencyKey,
                now);
        GenerationJob savedJob = generationJobRepository.save(job);
        eventPublisher.publishEvent(new GuidebookGenerationRequestedEvent(savedJob.getId()));
        // TODO: #42 UNIQUE 충돌을 기존 작업 조회로 복구한다.
        return GuidebookGenerationResponse.from(savedJob);
    }

    public GenerationStatusResponse getGenerationJobStatus(Long memberId, Long jobId) {
        if (!generationJobRepository.existsById(jobId)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!generationJobRepository.existsByIdAndMemberId(jobId, memberId)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_FORBIDDEN);
        }
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        GuidebookAiClient aiClient = guidebookAiClientProvider.getIfAvailable();
        if (aiClient != null && shouldSynchronize(job)) {
            AiGenerationStatusResponse aiResponse =
                    aiClient.getGenerationStatus(job.getAiJobId());
            guidebookResultService.apply(jobId, aiResponse);
            job = generationJobRepository.findById(jobId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        }

        GenerationStatusResponse.GenerationError error = job.getStatus() == GenerationStatus.FAILED
                ? new GenerationStatusResponse.GenerationError(
                        "GENERATION_FAILED", "가이드북 생성에 실패했습니다.")
                : null;
        return GenerationStatusResponse.from(job, error);
    }

    private boolean shouldSynchronize(GenerationJob job) {
        return job.getAiJobId() != null
                && (job.getStatus() == GenerationStatus.PENDING
                || job.getStatus() == GenerationStatus.PROCESSING);
    }

    private GuidebookGenerationResponse handleRepeatedRequest(
            GenerationJob existingJob,
            GuidebookGenerationRequest request) {
        InitialGenerationRequestPayload existingPayload = deserialize(
                existingJob.getRequestPayload());
        if (!existingPayload.request().equals(request)) {
            throw new BusinessException(GuidebookErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return GuidebookGenerationResponse.from(existingJob);
    }

    private void validateTravelCondition(GuidebookGenerationRequest request) {
        validateRegion(request.province(), request.city());

        LocalDate today = LocalDate.now(clock.withZone(SEOUL_ZONE));
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();

        if (startDate == null
                || endDate == null
                || startDate.isBefore(today)
                || endDate.isBefore(startDate)
                || endDate.isAfter(today.plusYears(1))
                || ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_TRIP_DAYS) {
            throw new BusinessException(GuidebookErrorCode.GUIDEBOOK_INVALID_PERIOD);
        }

        if (!isValidPeopleCount(request.companion(), request.peopleCount())) {
            throw new BusinessException(GuidebookErrorCode.GUIDEBOOK_INVALID_PARTY);
        }
    }

    private void validateRegion(String provinceName, String cityName) {
        try {
            AdministrativeProvince province =
                    AdministrativeProvince.fromDisplayName(provinceName);
            AdministrativeDistrict.fromDisplayName(province, cityName);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(GuidebookErrorCode.GUIDEBOOK_INVALID_REGION);
        }
    }

    private boolean isValidPeopleCount(Companion companion, Integer peopleCount) {
        if (companion == null || peopleCount == null) {
            return false;
        }
        return switch (companion) {
            case ALONE -> peopleCount == 1;
            case FRIEND -> peopleCount >= 2 && peopleCount <= 4;
            case COUPLE -> peopleCount == 2;
            case FAMILY -> peopleCount >= 2 && peopleCount <= 6;
            case GROUP -> peopleCount >= 2 && peopleCount <= 10;
        };
    }

    private InitialGenerationRequestPayload createInitialRequestPayload(
            GuidebookGenerationRequest request,
            List<MemberPreference> memberPreferences) {
        List<PreferenceSnapshot> preferenceSnapshots = memberPreferences.stream()
                .map(preference -> new PreferenceSnapshot(
                        preference.getPreferenceType().name(),
                        preference.getPreferenceCode().name()))
                .toList();
        return new InitialGenerationRequestPayload(request, preferenceSnapshots);
    }

    private String serialize(InitialGenerationRequestPayload requestPayload) {
        try {
            return objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("가이드북 생성 요청을 저장할 수 없습니다.", exception);
        }
    }

    private InitialGenerationRequestPayload deserialize(String requestPayload) {
        try {
            return objectMapper.readValue(requestPayload, InitialGenerationRequestPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 가이드북 생성 요청을 읽을 수 없습니다.", exception);
        }
    }
}
