package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.repository.GuidebookEventChangeNotificationQuery;
import com.ktb10.kgb.content.repository.GuidebookEventChangeNotificationTarget;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookEventChangeNotificationProcessorTest {

    private static final UUID RUN_ID =
            UUID.fromString("c783905d-904f-41b9-8016-cb8242533cc8");
    private static final ContentChangesDetectedEvent EVENT =
            new ContentChangesDetectedEvent(RUN_ID, LocalDate.of(2026, 11, 3), 2);

    @Mock
    private TourApiContentChangeOccurrenceRepository occurrenceRepository;

    @Mock
    private GuidebookEventChangeCandidateSelector candidateSelector;

    @Mock
    private GuidebookEventChangeNotificationQuery notificationQuery;

    @Mock
    private NotificationService notificationService;

    @Mock
    private GuidebookEventChangeNotificationMetrics metrics;

    private GuidebookEventChangeNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GuidebookEventChangeNotificationProcessor(
                occurrenceRepository,
                candidateSelector,
                notificationQuery,
                notificationService,
                metrics);
    }

    @Test
    void createsChangedAndCancelledNotificationsPerGuidebook() {
        given(candidateSelector.select(any())).willReturn(List.of(
                new GuidebookEventChangeCandidate(1L, "event-changed", false),
                new GuidebookEventChangeCandidate(2L, "event-cancelled", true)));
        given(notificationQuery.findTargets(any())).willReturn(List.of(
                new GuidebookEventChangeNotificationTarget(11L, 101L, "event-changed"),
                new GuidebookEventChangeNotificationTarget(12L, 102L, "event-cancelled")));
        given(notificationService.createOnce(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(true, true);

        GuidebookEventChangeNotificationProcessor.ProcessingResult result =
                processor.process(EVENT);

        assertThat(result).isEqualTo(
                new GuidebookEventChangeNotificationProcessor.ProcessingResult(2, 2, 0, 0));
        verify(notificationService).createOnce(
                11L,
                NotificationType.GUIDEBOOK_EVENT_CHANGED,
                "가이드북 일정에 변경 사항이 있어요",
                "가이드북에 포함된 행사의 일정이 변경되었어요. 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "101",
                "guidebook-event-change:1");
        verify(notificationService).createOnce(
                12L,
                NotificationType.GUIDEBOOK_EVENT_CANCELLED,
                "가이드북 일정에 변경 사항이 있어요",
                "가이드북에 포함된 행사가 취소되었어요. 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "102",
                "guidebook-event-change:2");
    }

    @Test
    void continuesAfterOneGuidebookNotificationFails() {
        given(candidateSelector.select(any())).willReturn(List.of(
                new GuidebookEventChangeCandidate(1L, "event-changed", false)));
        given(notificationQuery.findTargets(any())).willReturn(List.of(
                new GuidebookEventChangeNotificationTarget(11L, 101L, "event-changed"),
                new GuidebookEventChangeNotificationTarget(12L, 102L, "event-changed")));
        given(notificationService.createOnce(
                eq(11L), eq(NotificationType.GUIDEBOOK_EVENT_CHANGED), any(), any(),
                any(), any(), any()))
                .willThrow(new IllegalStateException("첫 회원 실패"));
        given(notificationService.createOnce(
                eq(12L), eq(NotificationType.GUIDEBOOK_EVENT_CHANGED), any(), any(),
                any(), any(), any()))
                .willReturn(false);

        GuidebookEventChangeNotificationProcessor.ProcessingResult result =
                processor.process(EVENT);

        assertThat(result).isEqualTo(
                new GuidebookEventChangeNotificationProcessor.ProcessingResult(2, 0, 1, 1));
        verify(metrics).recordFailed();
        verify(metrics).recordDuplicate();
    }
}
