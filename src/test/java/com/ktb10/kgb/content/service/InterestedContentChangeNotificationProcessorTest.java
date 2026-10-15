package com.ktb10.kgb.content.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.event.InterestedContentChangesDetectedEvent;
import com.ktb10.kgb.content.repository.InterestedContentNotificationQuery;
import com.ktb10.kgb.content.repository.InterestedContentNotificationTarget;
import com.ktb10.kgb.content.tourapi.ContentChangedField;
import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidate;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterestedContentChangeNotificationProcessorTest {

    private final InterestedContentNotificationQuery notificationQuery =
            mock(InterestedContentNotificationQuery.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final InterestedContentChangeNotificationProcessor processor =
            new InterestedContentChangeNotificationProcessor(
                    notificationQuery,
                    notificationService);

    @Test
    void createsOneNotificationForEachMemberAndContent() {
        InterestedContentChangesDetectedEvent event = event("100", "200");
        when(notificationQuery.findTargets(Set.of("100", "200"))).thenReturn(List.of(
                new InterestedContentNotificationTarget(1L, "100", "서울 불꽃축제"),
                new InterestedContentNotificationTarget(1L, "200", "가을 음악회"),
                new InterestedContentNotificationTarget(2L, "100", "서울 불꽃축제")));

        processor.process(event);

        verify(notificationService).create(
                1L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "100");
        verify(notificationService).create(
                1L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'가을 음악회'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "200");
        verify(notificationService).create(
                2L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "100");
    }

    @Test
    void continuesWithNextMemberWhenOneNotificationFails() {
        InterestedContentChangesDetectedEvent event = event("100");
        when(notificationQuery.findTargets(Set.of("100"))).thenReturn(List.of(
                new InterestedContentNotificationTarget(1L, "100", "서울 불꽃축제"),
                new InterestedContentNotificationTarget(2L, "100", "서울 불꽃축제")));
        doThrow(new IllegalStateException("알림 저장 실패"))
                .when(notificationService)
                .create(
                        1L,
                        NotificationType.EVENT_CHANGED,
                        "관심 행사 정보가 변경됐어요",
                        "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                        NotificationReferenceType.EVENT,
                        "100");

        processor.process(event);

        verify(notificationService).create(
                2L,
                NotificationType.EVENT_CHANGED,
                "관심 행사 정보가 변경됐어요",
                "'서울 불꽃축제'의 새로운 정보를 확인해 보세요.",
                NotificationReferenceType.EVENT,
                "100");
    }

    private InterestedContentChangesDetectedEvent event(String... sourceContentIds) {
        return new InterestedContentChangesDetectedEvent(
                LocalDate.of(2026, 10, 15),
                java.util.Arrays.stream(sourceContentIds)
                        .map(sourceContentId -> new InterestedContentChangeCandidate(
                                sourceContentId,
                                Set.of(ContentChangedField.DESCRIPTION)))
                        .toList());
    }
}
