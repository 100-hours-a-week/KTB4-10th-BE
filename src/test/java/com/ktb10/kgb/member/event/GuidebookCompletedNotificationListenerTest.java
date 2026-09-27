package com.ktb10.kgb.member.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.guidebook.event.GuidebookCompletedEvent;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuidebookCompletedNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Test
    void createsGuidebookCompletedNotification() {
        GuidebookCompletedNotificationListener listener =
                new GuidebookCompletedNotificationListener(notificationService);

        listener.handle(new GuidebookCompletedEvent(1L, 501L, "경주 여행"));

        verify(notificationService).create(
                1L,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "'경주 여행' 가이드북이 완성되었습니다.",
                NotificationReferenceType.GUIDEBOOK,
                "501");
    }

    @Test
    void doesNotPropagateNotificationFailureAfterGuidebookCommit() {
        GuidebookCompletedNotificationListener listener =
                new GuidebookCompletedNotificationListener(notificationService);
        doThrow(new IllegalStateException("알림 저장 실패"))
                .when(notificationService)
                .create(
                        1L,
                        NotificationType.GUIDEBOOK_COMPLETED,
                        "가이드북 생성 완료",
                        "'경주 여행' 가이드북이 완성되었습니다.",
                        NotificationReferenceType.GUIDEBOOK,
                        "501");

        assertThatCode(() -> listener.handle(
                new GuidebookCompletedEvent(1L, 501L, "경주 여행")))
                .doesNotThrowAnyException();
    }
}
