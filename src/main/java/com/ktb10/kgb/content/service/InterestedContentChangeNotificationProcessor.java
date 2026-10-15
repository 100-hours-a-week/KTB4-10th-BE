package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.event.InterestedContentChangesDetectedEvent;
import com.ktb10.kgb.content.repository.InterestedContentNotificationQuery;
import com.ktb10.kgb.content.repository.InterestedContentNotificationTarget;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 관심 행사 변경 후보를 회원별 인앱·실시간 알림으로 변환합니다. */
@Service
public class InterestedContentChangeNotificationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            InterestedContentChangeNotificationProcessor.class);
    private static final String TITLE = "관심 행사 정보가 변경됐어요";
    private static final String BODY_FORMAT = "'%s'의 새로운 정보를 확인해 보세요.";

    private final InterestedContentNotificationQuery notificationQuery;
    private final NotificationService notificationService;

    public InterestedContentChangeNotificationProcessor(
            InterestedContentNotificationQuery notificationQuery,
            NotificationService notificationService) {
        this.notificationQuery = notificationQuery;
        this.notificationService = notificationService;
    }

    public void process(InterestedContentChangesDetectedEvent event) {
        Set<String> sourceContentIds = event.candidates().stream()
                .map(candidate -> candidate.sourceContentId())
                .collect(Collectors.toSet());
        int completed = 0;
        int failed = 0;
        for (InterestedContentNotificationTarget target
                : notificationQuery.findTargets(sourceContentIds)) {
            try {
                notificationService.create(
                        target.memberId(),
                        NotificationType.EVENT_CHANGED,
                        TITLE,
                        BODY_FORMAT.formatted(target.contentTitle()),
                        NotificationReferenceType.EVENT,
                        target.sourceContentId());
                completed++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.error(
                        "관심 행사 변경 알림 생성에 실패했습니다. memberId={}, contentId={}",
                        target.memberId(),
                        target.sourceContentId(),
                        exception);
            }
        }
        LOGGER.info(
                "관심 행사 변경 알림 처리를 완료했습니다. syncDate={}, completed={}, failed={}",
                event.syncDate(),
                completed,
                failed);
    }
}
