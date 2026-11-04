package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.repository.InterestedContentNotificationQuery;
import com.ktb10.kgb.content.repository.InterestedContentNotificationTarget;
import com.ktb10.kgb.content.tourapi.ContentChangedField;
import com.ktb10.kgb.content.tourapi.ContentLifecycleChange;
import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidate;
import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidateSelector;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrence;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterestedContentChangeNotificationProcessorTest {

    private static final UUID RUN_ID =
            UUID.fromString("7e85cb87-3d65-4dc6-832e-a5303030af31");

    private final TourApiContentChangeOccurrenceRepository occurrenceRepository =
            mock(TourApiContentChangeOccurrenceRepository.class);
    private final InterestedContentChangeCandidateSelector candidateSelector =
            mock(InterestedContentChangeCandidateSelector.class);
    private final InterestedContentNotificationQuery notificationQuery =
            mock(InterestedContentNotificationQuery.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final InterestedContentChangeNotificationMetrics metrics =
            mock(InterestedContentChangeNotificationMetrics.class);
    private final InterestedContentChangeNotificationProcessor processor =
            new InterestedContentChangeNotificationProcessor(
                    occurrenceRepository,
                    candidateSelector,
                    notificationQuery,
                    notificationService,
                    metrics);

    @Test
    void createsIdempotentNotificationForEachMemberAndOccurrence() {
        ContentChangesDetectedEvent event = event(2);
        TourApiContentChangeOccurrence first = occurrence(11L, "100");
        TourApiContentChangeOccurrence second = occurrence(12L, "200");
        when(occurrenceRepository.findAllByRunId(RUN_ID)).thenReturn(List.of(first, second));
        when(candidateSelector.select(List.of(first, second))).thenReturn(List.of(
                candidate(11L, "100"),
                candidate(12L, "200")));
        when(notificationQuery.findTargets(Set.of("100", "200"))).thenReturn(List.of(
                new InterestedContentNotificationTarget(1L, "100", "서울 불꽃축제"),
                new InterestedContentNotificationTarget(1L, "200", "가을 음악회")));
        when(notificationService.createOnce(
                1L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "100",
                "tour-api-change:11"))
                .thenReturn(true);
        when(notificationService.createOnce(
                1L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'가을 음악회'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "200",
                "tour-api-change:12"))
                .thenReturn(false);

        var result = processor.process(event);

        assertThat(result).isEqualTo(
                new InterestedContentChangeNotificationProcessor.ProcessingResult(2, 1, 1, 0));
        verify(metrics).recordTargets(2);
        verify(metrics).recordCreated();
        verify(metrics).recordDuplicateSkipped();
    }

    @Test
    void skipsRunWithoutUserMeaningfulChange() {
        ContentChangesDetectedEvent event = event(1);
        TourApiContentChangeOccurrence occurrence = occurrence(11L, "100");
        when(occurrenceRepository.findAllByRunId(RUN_ID)).thenReturn(List.of(occurrence));
        when(candidateSelector.select(List.of(occurrence))).thenReturn(List.of());

        var result = processor.process(event);

        assertThat(result).isEqualTo(
                new InterestedContentChangeNotificationProcessor.ProcessingResult(0, 0, 0, 0));
        verify(notificationQuery, never()).findTargets(anySet());
    }

    @Test
    void continuesWithNextMemberWhenOneNotificationFails() {
        ContentChangesDetectedEvent event = event(1);
        TourApiContentChangeOccurrence occurrence = occurrence(11L, "100");
        when(occurrenceRepository.findAllByRunId(RUN_ID)).thenReturn(List.of(occurrence));
        when(candidateSelector.select(List.of(occurrence)))
                .thenReturn(List.of(candidate(11L, "100")));
        when(notificationQuery.findTargets(Set.of("100"))).thenReturn(List.of(
                new InterestedContentNotificationTarget(1L, "100", "서울 불꽃축제"),
                new InterestedContentNotificationTarget(2L, "100", "서울 불꽃축제")));
        doThrow(new IllegalStateException("알림 저장 실패"))
                .when(notificationService)
                .createOnce(
                        1L,
                        NotificationType.EVENT_CHANGED,
                        "관심 행사 정보가 변경됐어요",
                        "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                        NotificationReferenceType.EVENT,
                        "100",
                        "tour-api-change:11");
        when(notificationService.createOnce(
                2L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "100",
                "tour-api-change:11"))
                .thenReturn(true);

        var result = processor.process(event);

        assertThat(result).isEqualTo(
                new InterestedContentChangeNotificationProcessor.ProcessingResult(2, 1, 0, 1));
        verify(metrics).recordDeliveryFailed();
        verify(metrics).recordCreated();
    }

    private ContentChangesDetectedEvent event(int occurrenceCount) {
        return new ContentChangesDetectedEvent(
                RUN_ID,
                LocalDate.of(2026, 10, 15),
                occurrenceCount);
    }

    private TourApiContentChangeOccurrence occurrence(Long id, String sourceContentId) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 0, 0);
        return new TourApiContentChangeOccurrence(
                id,
                RUN_ID,
                sourceContentId,
                Set.of(ContentChangedField.DESCRIPTION),
                ContentLifecycleChange.NONE,
                null,
                null,
                now,
                now);
    }

    private InterestedContentChangeCandidate candidate(Long id, String sourceContentId) {
        return new InterestedContentChangeCandidate(
                id,
                sourceContentId,
                Set.of(ContentChangedField.DESCRIPTION));
    }
}
