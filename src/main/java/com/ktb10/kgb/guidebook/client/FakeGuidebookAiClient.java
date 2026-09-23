package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Coordinates;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.GuidebookResult;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.ItineraryDay;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Place;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 실제 AI 서버 없이 로컬 생성 흐름을 실행하기 위한 Client입니다. */
@Component
@Profile("local")
public class FakeGuidebookAiClient implements GuidebookAiClient {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, FakeJob> jobs = new ConcurrentHashMap<>();

    @Override
    public AiGenerationAcceptedResponse requestGeneration(AiGuidebookRequest request) {
        String jobId = "fake-job-" + sequence.incrementAndGet();
        jobs.put(jobId, new FakeJob(request));
        return new AiGenerationAcceptedResponse(jobId, AiGenerationStatus.PENDING);
    }

    @Override
    public AiGenerationStatusResponse getGenerationStatus(String aiJobId) {
        FakeJob job = findJob(aiJobId);
        if (job.statusChecks().getAndIncrement() == 0) {
            return AiGenerationStatusResponse.processing(aiJobId);
        }
        return AiGenerationStatusResponse.completed(
                aiJobId,
                createResult(job.request()));
    }

    @Override
    public AiGenerationAcceptedResponse retryGeneration(String aiJobId) {
        FakeJob job = findJob(aiJobId);
        job.statusChecks().set(0);
        return new AiGenerationAcceptedResponse(aiJobId, AiGenerationStatus.PENDING);
    }

    private FakeJob findJob(String aiJobId) {
        FakeJob job = jobs.get(aiJobId);
        if (job == null) {
            throw new AiClientException("AI 생성 작업을 찾을 수 없습니다: " + aiJobId);
        }
        return job;
    }

    private GuidebookResult createResult(AiGuidebookRequest request) {
        List<ItineraryDay> itinerary = request.startDate()
                .datesUntil(request.endDate().plusDays(1))
                .map(date -> createDay(request, date))
                .toList();
        return new GuidebookResult(
                request.region().city(),
                request.region().city() + " 여행 가이드북",
                "로컬 개발을 위한 Fake AI 생성 결과입니다.",
                itinerary);
    }

    private ItineraryDay createDay(AiGuidebookRequest request, LocalDate date) {
        int dayNumber = Math.toIntExact(
                ChronoUnit.DAYS.between(request.startDate(), date) + 1);
        Place place = new Place(
                1,
                LocalTime.of(9, 0),
                "fake-content-" + dayNumber,
                request.region().city() + " 테스트 장소",
                "관광",
                "Fake AI가 생성한 테스트 장소입니다.",
                "선택한 취향을 확인하기 위한 추천입니다.",
                "로컬 환경에서만 사용하는 데이터입니다.",
                60,
                request.region().province() + " " + request.region().city(),
                new Coordinates(35.0, 129.0),
                null);
        return new ItineraryDay(dayNumber, date, List.of(place));
    }

    private record FakeJob(
            AiGuidebookRequest request,
            AtomicInteger statusChecks) {

        private FakeJob(AiGuidebookRequest request) {
            this(request, new AtomicInteger());
        }
    }
}
