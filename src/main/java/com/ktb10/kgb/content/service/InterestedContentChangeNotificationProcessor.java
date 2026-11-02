package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.event.InterestedContentChangesDetectedEvent;
import com.ktb10.kgb.content.repository.InterestedContentNotificationQuery;
import com.ktb10.kgb.content.repository.InterestedContentNotificationTarget;
import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidate;
import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidateSelector;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private static final String OCCURRENCE_KEY_FORMAT = "tour-api-change:%d";

    private final TourApiContentChangeOccurrenceRepository changeOccurrenceRepository;
    private final InterestedContentChangeCandidateSelector candidateSelector;
    private final InterestedContentNotificationQuery notificationQuery;
    private final NotificationService notificationService;
    private final InterestedContentChangeNotificationMetrics metrics;

    public InterestedContentChangeNotificationProcessor(
            TourApiContentChangeOccurrenceRepository changeOccurrenceRepository,
            InterestedContentChangeCandidateSelector candidateSelector,
            InterestedContentNotificationQuery notificationQuery,
            NotificationService notificationService,
            InterestedContentChangeNotificationMetrics metrics) {
        this.changeOccurrenceRepository = changeOccurrenceRepository;
        this.candidateSelector = candidateSelector;
        this.notificationQuery = notificationQuery;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    /** 같은 runId를 다시 처리해도 회원별 occurrence는 한 번만 생성됩니다. */
    public ProcessingResult process(InterestedContentChangesDetectedEvent event) {
        Map<String, InterestedContentChangeCandidate> candidates = candidateSelector.select(
                        changeOccurrenceRepository.findAllByRunId(event.runId()))
                .stream()
                .collect(Collectors.toMap(
                        InterestedContentChangeCandidate::sourceContentId,
                        Function.identity()));
        Set<String> sourceContentIds = candidates.values().stream()
                .map(candidate -> candidate.sourceContentId())
                .collect(Collectors.toSet());
        if (sourceContentIds.isEmpty()) {
            LOGGER.info(
                    "관심 행사 변경 알림 대상 이력이 없습니다. runId={}, syncDate={}",
                    event.runId(),
                    event.syncDate());
            return new ProcessingResult(0, 0, 0, 0);
        }

        int targets = 0;
        int created = 0;
        int duplicate = 0;
        int failed = 0;
        for (InterestedContentNotificationTarget target
                : notificationQuery.findTargets(sourceContentIds)) {
            targets++;
            InterestedContentChangeCandidate candidate = candidates.get(target.sourceContentId());
            try {
                boolean notificationCreated = notificationService.createOnce(
                        target.memberId(),
                        NotificationType.EVENT_CHANGED,
                        TITLE,
                        BODY_FORMAT.formatted(target.contentTitle()),
                        NotificationReferenceType.EVENT,
                        target.sourceContentId(),
                        OCCURRENCE_KEY_FORMAT.formatted(candidate.occurrenceId()));
                if (notificationCreated) {
                    created++;
                    metrics.recordCreated();
                } else {
                    duplicate++;
                    metrics.recordDuplicateSkipped();
                }
            } catch (RuntimeException exception) {
                failed++;
                metrics.recordDeliveryFailed();
                LOGGER.error(
                        "관심 행사 변경 알림 생성에 실패했습니다. memberId={}, contentId={}",
                        target.memberId(),
                        target.sourceContentId(),
                        exception);
            }
        }
        metrics.recordTargets(targets);
        LOGGER.info(
                "관심 행사 변경 알림 처리를 완료했습니다. runId={}, syncDate={}, "
                        + "targets={}, created={}, duplicate={}, failed={}",
                event.runId(),
                event.syncDate(),
                targets,
                created,
                duplicate,
                failed);
        return new ProcessingResult(targets, created, duplicate, failed);
    }

    public record ProcessingResult(int targets, int created, int duplicate, int failed) {
    }
}
