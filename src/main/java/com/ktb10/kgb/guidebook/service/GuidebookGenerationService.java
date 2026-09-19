package com.ktb10.kgb.guidebook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.response.GuidebookGenerationResponse;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최초 가이드북 생성 요청을 작업으로 접수합니다. */
@Service
public class GuidebookGenerationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_TRIP_DAYS = 7;
    private static final Set<GenerationStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);

    private final GenerationJobRepository generationJobRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GuidebookGenerationService(
            GenerationJobRepository generationJobRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.generationJobRepository = generationJobRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public GuidebookGenerationResponse createInitial(
            Long memberId,
            String idempotencyKey,
            GuidebookGenerationRequest request) {
        String requestPayload = serialize(request);
        Optional<GenerationJob> existingJob =
                generationJobRepository.findByMemberIdAndIdempotencyKey(
                        memberId,
                        idempotencyKey);
        if (existingJob.isPresent()) {
            return handleRepeatedRequest(existingJob.get(), requestPayload);
        }

        validateTravelCondition(request);

        // TODO: #40 회원 행을 잠근 뒤 ACTIVE 상태를 재검증하고 현재 취향을 조회한다.
        // TODO: #40 멱등 비교용 요청과 취향 스냅샷을 구분해 requestPayload에 저장한다.
        // TODO: #40 생성권 지갑을 잠근 뒤 잔액을 확인한다.
        if (generationJobRepository.existsByMemberIdAndStatusIn(memberId, ACTIVE_STATUSES)) {
            throw new BusinessException(GuidebookErrorCode.GENERATION_IN_PROGRESS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        GenerationJob job = GenerationJob.createInitial(
                memberId,
                requestPayload,
                idempotencyKey,
                now);
        GenerationJob savedJob = generationJobRepository.save(job);
        // TODO: #40 UNIQUE 충돌을 기존 작업 조회로 복구하고 커밋 후 AI 생성을 트리거한다.
        return GuidebookGenerationResponse.from(savedJob);
    }

    private GuidebookGenerationResponse handleRepeatedRequest(
            GenerationJob existingJob,
            String requestPayload) {
        if (!existingJob.getRequestPayload().equals(requestPayload)) {
            throw new BusinessException(GuidebookErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return GuidebookGenerationResponse.from(existingJob);
    }

    private void validateTravelCondition(GuidebookGenerationRequest request) {
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

    private String serialize(GuidebookGenerationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("가이드북 생성 요청을 저장할 수 없습니다.", exception);
        }
    }
}
