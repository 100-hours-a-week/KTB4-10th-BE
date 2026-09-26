package com.ktb10.kgb.guidebook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.GuidebookResult;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Place;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.entity.Region;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.guidebook.repository.RegionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 작업 상태와 완료 결과를 가이드북 도메인에 반영합니다. */
@Service
public class GuidebookResultService {

    private static final int GENERATION_CREDIT_COST = 1;

    private final GenerationJobRepository generationJobRepository;
    private final GuidebookRepository guidebookRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final MemberGuidebookRepository memberGuidebookRepository;
    private final RegionRepository regionRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GuidebookResultService(
            GenerationJobRepository generationJobRepository,
            GuidebookRepository guidebookRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            MemberGuidebookRepository memberGuidebookRepository,
            RegionRepository regionRepository,
            CreditWalletRepository creditWalletRepository,
            CreditTransactionRepository creditTransactionRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.generationJobRepository = generationJobRepository;
        this.guidebookRepository = guidebookRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.memberGuidebookRepository = memberGuidebookRepository;
        this.regionRepository = regionRepository;
        this.creditWalletRepository = creditWalletRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void apply(Long jobId, AiGenerationStatusResponse response) {
        GenerationJob job = generationJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (isTerminal(job.getStatus())) {
            return;
        }
        validateResponse(job, response);
        LocalDateTime now = LocalDateTime.now(clock);

        switch (response.status()) {
            case PENDING -> {
                // AI 작업이 대기 중이므로 내부 상태를 유지한다.
            }
            case PROCESSING -> job.markProcessing(now);
            case COMPLETED -> persistCompletedResult(job, response.result(), now);
            case FAILED -> job.fail(serialize(response.error()), now);
            default -> throw new IllegalStateException(
                    "지원하지 않는 AI 작업 상태입니다: " + response.status());
        }
    }

    private void persistCompletedResult(
            GenerationJob job,
            GuidebookResult result,
            LocalDateTime now) {
        if (result == null) {
            throw new IllegalStateException("AI 완료 응답에 가이드북 결과가 없습니다.");
        }
        InitialGenerationRequestPayload payload = deserialize(job.getRequestPayload());
        GuidebookGenerationRequest request = payload.request();
        Region region = regionRepository.findByName(request.province())
                .orElseThrow(() -> new IllegalStateException(
                        "가이드북 지역 기준 정보를 찾을 수 없습니다: " + request.province()));

        validateItinerary(result, request);
        Guidebook guidebook = guidebookRepository.save(Guidebook.create(
                result.title(),
                region.getId(),
                request.startDate(),
                request.endDate(),
                request.companion(),
                request.peopleCount(),
                result.summary(),
                now));

        List<AiGenerationStatusResponse.ItineraryDay> days = result.itinerary().stream()
                .sorted(Comparator.comparingInt(AiGenerationStatusResponse.ItineraryDay::day))
                .toList();
        for (AiGenerationStatusResponse.ItineraryDay resultDay : days) {
            ItineraryDay day = itineraryDayRepository.save(ItineraryDay.create(
                    guidebook, resultDay.day(), resultDay.date()));
            List<Place> places = resultDay.places().stream()
                    .sorted(Comparator.comparingInt(Place::order))
                    .toList();
            for (Place place : places) {
                itineraryItemRepository.save(ItineraryItem.create(
                        day,
                        null,
                        place.order(),
                        place.time(),
                        serialize(place),
                        now));
            }
        }

        memberGuidebookRepository.save(MemberGuidebook.create(
                job.getMember(), guidebook, AcquisitionType.CREATED, now));
        consumeCredit(job, now);
        job.complete(guidebook.getId(), now);
    }

    private void consumeCredit(GenerationJob job, LocalDateTime now) {
        String ledgerKey = "generation-consume:" + job.getId();
        if (creditTransactionRepository.existsByIdempotencyKey(ledgerKey)) {
            throw new IllegalStateException("이미 생성권이 차감된 작업입니다.");
        }
        CreditWallet wallet = creditWalletRepository
                .findByMemberIdForUpdate(job.getMember().getId())
                .orElseThrow(() -> new BusinessException(GuidebookErrorCode.CREDIT_INSUFFICIENT));
        int balanceAfter;
        try {
            balanceAfter = wallet.consume(GENERATION_CREDIT_COST, now);
        } catch (IllegalStateException exception) {
            throw new BusinessException(GuidebookErrorCode.CREDIT_INSUFFICIENT);
        }
        creditTransactionRepository.save(CreditTransaction.consume(
                wallet,
                job.getId(),
                balanceAfter,
                ledgerKey,
                now));
    }

    private void validateItinerary(
            GuidebookResult result,
            GuidebookGenerationRequest request) {
        List<AiGenerationStatusResponse.ItineraryDay> days = result.itinerary().stream()
                .sorted(Comparator.comparingInt(AiGenerationStatusResponse.ItineraryDay::day))
                .toList();
        long expectedDays = request.startDate().datesUntil(request.endDate().plusDays(1)).count();
        if (days.size() != expectedDays) {
            throw new IllegalStateException("AI 일정 일수가 요청 기간과 다릅니다.");
        }
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            AiGenerationStatusResponse.ItineraryDay day = days.get(dayIndex);
            int expectedDay = dayIndex + 1;
            if (day.day() != expectedDay
                    || !day.date().equals(request.startDate().plusDays(dayIndex))) {
                throw new IllegalStateException("AI 일정의 일차와 날짜가 연속적이지 않습니다.");
            }
            List<Integer> orders = day.places().stream()
                    .map(Place::order)
                    .sorted()
                    .toList();
            for (int orderIndex = 0; orderIndex < orders.size(); orderIndex++) {
                if (orders.get(orderIndex) != orderIndex + 1) {
                    throw new IllegalStateException("AI 일정의 방문 순서가 연속적이지 않습니다.");
                }
            }
        }
    }

    private void validateResponse(GenerationJob job, AiGenerationStatusResponse response) {
        if (response == null
                || response.status() == null
                || !job.getAiJobId().equals(response.jobId())) {
            throw new IllegalStateException("AI 작업 상태 응답이 올바르지 않습니다.");
        }
    }

    private boolean isTerminal(GenerationStatus status) {
        return status == GenerationStatus.COMPLETED
                || status == GenerationStatus.FAILED
                || status == GenerationStatus.CANCELED;
    }

    private InitialGenerationRequestPayload deserialize(String requestPayload) {
        try {
            return objectMapper.readValue(requestPayload, InitialGenerationRequestPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 가이드북 생성 요청을 읽을 수 없습니다.", exception);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("가이드북 생성 결과를 저장할 수 없습니다.", exception);
        }
    }
}
