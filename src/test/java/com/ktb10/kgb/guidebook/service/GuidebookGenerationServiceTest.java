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
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.error.GuidebookErrorCode;
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
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
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

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPreferenceRepository memberPreferenceRepository;

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
        given(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(MEMBER_ID))
                .willReturn(preferences(member));
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
        given(generationJobRepository.save(any(GenerationJob.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request);

        assertThat(response.jobType().name()).isEqualTo("INITIAL");
        assertThat(response.status()).isEqualTo(GenerationStatus.PENDING);
        assertThat(response.guidebookId()).isNull();
        verify(generationJobRepository).save(any(GenerationJob.class));
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
        given(memberPreferenceRepository
                .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(MEMBER_ID))
                .willReturn(preferences(member));
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
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
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
        given(generationJobRepository.save(any(GenerationJob.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createInitial(MEMBER_ID, IDEMPOTENCY_KEY, request).status())
                .isEqualTo(GenerationStatus.PENDING);
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
        return Member.register(
                OauthProvider.KAKAO,
                "guidebook-service-member",
                "여행자",
                null,
                null,
                LocalDate.of(2026, 9, 19).atStartOfDay());
    }
}
