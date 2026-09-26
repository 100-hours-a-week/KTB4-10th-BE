package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Coordinates;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.GuidebookResult;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.ItineraryDay;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Place;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.Region;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.guidebook.repository.RegionRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuidebookResultServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-27T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private GenerationJobRepository generationJobRepository;
    @Mock
    private GuidebookRepository guidebookRepository;
    @Mock
    private ItineraryDayRepository itineraryDayRepository;
    @Mock
    private ItineraryItemRepository itineraryItemRepository;
    @Mock
    private MemberGuidebookRepository memberGuidebookRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private CreditWalletRepository creditWalletRepository;
    @Mock
    private CreditTransactionRepository creditTransactionRepository;
    private ObjectMapper objectMapper;
    private GuidebookResultService resultService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        resultService = new GuidebookResultService(
                generationJobRepository,
                guidebookRepository,
                itineraryDayRepository,
                itineraryItemRepository,
                memberGuidebookRepository,
                regionRepository,
                creditWalletRepository,
                creditTransactionRepository,
                objectMapper,
                CLOCK);
    }

    @Test
    void marksProcessingJob() throws Exception {
        GenerationJob job = job();
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        resultService.apply(301L, AiGenerationStatusResponse.processing("ai-job-301"));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
        assertThat(job.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void savesCompletedResultAndConsumesCredit() throws Exception {
        GenerationJob job = job();
        Region region = Region.create("47", "경상북도");
        ReflectionTestUtils.setField(region, "id", 47L);
        CreditWallet wallet = CreditWallet.open(job.getMember(), NOW.minusDays(1));
        wallet.grant(3, NOW.minusDays(1));

        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));
        given(regionRepository.findByName("경상북도")).willReturn(Optional.of(region));
        given(guidebookRepository.save(any(Guidebook.class))).willAnswer(invocation -> {
            Guidebook guidebook = invocation.getArgument(0);
            ReflectionTestUtils.setField(guidebook, "id", 501L);
            return guidebook;
        });
        given(itineraryDayRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(creditWalletRepository.findByMemberIdForUpdate(1L))
                .willReturn(Optional.of(wallet));

        resultService.apply(
                301L, AiGenerationStatusResponse.completed("ai-job-301", result()));

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(job.getGuidebookId()).isEqualTo(501L);
        assertThat(job.getCompletedAt()).isEqualTo(NOW);
        assertThat(wallet.getCreditBalance()).isEqualTo(2);
        verify(itineraryDayRepository).save(any());
        verify(itineraryItemRepository).save(any());
        verify(memberGuidebookRepository).save(any());
        verify(creditTransactionRepository).save(any(CreditTransaction.class));
    }

    @Test
    void doesNotRequestAiAgainForCompletedJob() throws Exception {
        GenerationJob job = job();
        ReflectionTestUtils.setField(job, "status", GenerationStatus.COMPLETED);
        ReflectionTestUtils.setField(job, "guidebookId", 501L);
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        resultService.apply(301L, AiGenerationStatusResponse.processing("ai-job-301"));

        verify(guidebookRepository, never()).save(any());
    }

    @Test
    void recordsFailedAiResponseWithoutSavingGuidebook() throws Exception {
        GenerationJob job = job();
        AiGenerationStatusResponse response = new AiGenerationStatusResponse(
                "ai-job-301",
                AiGenerationStatus.FAILED,
                null,
                new AiGenerationStatusResponse.GenerationError("AI_FAILED", "failure"));
        given(generationJobRepository.findByIdForUpdate(301L)).willReturn(Optional.of(job));

        resultService.apply(301L, response);

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.getErrorPayload()).contains("AI_FAILED");
        verify(guidebookRepository, never()).save(any());
        verify(creditTransactionRepository, never()).save(any());
    }

    private GenerationJob job() throws Exception {
        Member member = Member.register(
                OauthProvider.KAKAO, "subject", "traveler", null, null, NOW.minusDays(1));
        ReflectionTestUtils.setField(member, "id", 1L);
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "경상북도",
                "경주시",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1),
                Companion.COUPLE,
                2);
        String payload = objectMapper.writeValueAsString(new InitialGenerationRequestPayload(
                request,
                List.of(new PreferenceSnapshot("THEME", "HEALING"))));
        GenerationJob job = GenerationJob.createInitial(member, payload, "key-301", NOW);
        ReflectionTestUtils.setField(job, "id", 301L);
        job.registerAiJob("ai-job-301", NOW);
        return job;
    }

    private GuidebookResult result() {
        Place place = new Place(
                1,
                LocalTime.of(9, 0),
                "content-1",
                "첨성대",
                "관광",
                "설명",
                "추천 이유",
                "팁",
                60,
                "경상북도 경주시",
                new Coordinates(35.8, 129.2),
                null);
        return new GuidebookResult(
                "경주시",
                "경주 여행",
                "경주 여행 요약",
                List.of(new ItineraryDay(
                        1, LocalDate.of(2026, 10, 1), List.of(place))));
    }
}
