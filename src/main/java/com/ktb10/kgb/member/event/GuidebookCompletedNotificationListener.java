package com.ktb10.kgb.member.event;

import com.ktb10.kgb.guidebook.event.GuidebookCompletedEvent;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 가이드북 완료 커밋 이후 회원의 인앱 알림을 생성합니다. */
@Component
public class GuidebookCompletedNotificationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            GuidebookCompletedNotificationListener.class);
    private static final String TITLE = "가이드북 생성 완료";
    private static final String BODY_FORMAT = "'%s' 가이드북이 완성되었습니다.";

    private final NotificationService notificationService;

    public GuidebookCompletedNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GuidebookCompletedEvent event) {
        try {
            notificationService.create(
                    event.memberId(),
                    NotificationType.GUIDEBOOK_COMPLETED,
                    TITLE,
                    BODY_FORMAT.formatted(event.guidebookTitle()),
                    NotificationReferenceType.GUIDEBOOK,
                    event.guidebookId().toString());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "가이드북 생성 완료 알림 저장에 실패했습니다. memberId={}, guidebookId={}",
                    event.memberId(),
                    event.guidebookId(),
                    exception);
        }
    }
}
