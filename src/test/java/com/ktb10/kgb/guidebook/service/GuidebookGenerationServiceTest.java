package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "initial-request-1";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-19T03:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private GenerationJobRepository generationJobRepository;

    private ObjectMapper objectMapper;
    private GuidebookGenerationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new GuidebookGenerationService(generationJobRepository, objectMapper, CLOCK);
    }

    @Test
    void createsPendingInitialJob() {
        GuidebookGenerationRequest request = validRequest();
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(generationJobRepository.existsByMemberIdAndStatusIn(
                any(Long.class), org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .willReturn(false);
        given(generationJobRepository.save(any(GenerationJob.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request);

        assertThat(response.jobType().name()).isEqualTo("INITIAL");
        assertThat(response.status()).isEqualTo(GenerationStatus.PENDING);
        assertThat(response.guidebookId()).isNull();
        verify(generationJobRepository).save(any(GenerationJob.class));
    }

    @Test
    void returnsExistingJobForSameIdempotentRequest() throws Exception {
        GuidebookGenerationRequest request = validRequest();
        GenerationJob existingJob = GenerationJob.createInitial(
                MEMBER_ID,
                objectMapper.writeValueAsString(request),
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
                MEMBER_ID,
                objectMapper.writeValueAsString(validRequest()),
                IDEMPOTENCY_KEY,
                LocalDate.of(2026, 9, 19).atStartOfDay());
        given(generationJobRepository.findByMemberIdAndIdempotencyKey(
                MEMBER_ID, IDEMPOTENCY_KEY)).willReturn(Optional.of(existingJob));
        GuidebookGenerationRequest differentRequest = new GuidebookGenerationRequest(
                "11",
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
    void rejectsTripLongerThanSevenDays() {
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "47",
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
                "47",
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

    private GuidebookGenerationRequest validRequest() {
        return new GuidebookGenerationRequest(
                "47",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2);
    }
}
