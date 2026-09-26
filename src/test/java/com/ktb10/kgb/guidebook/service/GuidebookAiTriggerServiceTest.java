package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.GuidebookAiClient;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookAiTriggerServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-26T12:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private GenerationJobRepository generationJobRepository;

    @Mock
    private GuidebookAiClient guidebookAiClient;

    private ObjectMapper objectMapper;
    private AiGuidebookRequestMapper mapper;
    private GuidebookAiTriggerService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mapper = new AiGuidebookRequestMapper();
        service = new GuidebookAiTriggerService(
                generationJobRepository,
                guidebookAiClient,
                mapper,
                objectMapper,
                CLOCK);
    }

    @Test
    void requestsAiGenerationAndRegistersExternalJobId() throws Exception {
        GenerationJob job = pendingJob();
        given(generationJobRepository.findById(301L)).willReturn(Optional.of(job));
        given(guidebookAiClient.requestGeneration(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AiGenerationAcceptedResponse(
                        "ai-job-301",
                        AiGenerationStatus.PENDING));

        service.trigger(301L);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(AiGuidebookRequest.class);
        verify(guidebookAiClient).requestGeneration(requestCaptor.capture());
        assertThat(requestCaptor.getValue().region().province()).isEqualTo("경상북도");
        assertThat(requestCaptor.getValue().preferences().largeCategory())
                .containsExactly("자연");
        assertThat(job.getAiJobId()).isEqualTo("ai-job-301");
    }

    @Test
    void doesNotRequestAiGenerationAgainWhenExternalJobIsAlreadyRegistered()
            throws Exception {
        GenerationJob job = pendingJob();
        job.registerAiJob("existing-ai-job", LocalDate.of(2026, 9, 26).atStartOfDay());
        given(generationJobRepository.findById(301L)).willReturn(Optional.of(job));

        service.trigger(301L);

        verify(guidebookAiClient, never())
                .requestGeneration(org.mockito.ArgumentMatchers.any());
    }

    private GenerationJob pendingJob() throws Exception {
        InitialGenerationRequestPayload payload = new InitialGenerationRequestPayload(
                new GuidebookGenerationRequest(
                        "경상북도",
                        "경주시",
                        LocalDate.of(2026, 10, 12),
                        LocalDate.of(2026, 10, 14),
                        Companion.FRIEND,
                        2),
                List.of(
                        new PreferenceSnapshot("THEME", "NATURE"),
                        new PreferenceSnapshot("DETAIL", "NATURE_MOUNTAIN")));
        return GenerationJob.createInitial(
                member(),
                objectMapper.writeValueAsString(payload),
                "request-key",
                LocalDate.of(2026, 9, 26).atStartOfDay());
    }

    private Member member() {
        return Member.register(
                OauthProvider.KAKAO,
                "ai-trigger-member",
                "여행자",
                null,
                null,
                LocalDate.of(2026, 9, 26).atStartOfDay());
    }
}
