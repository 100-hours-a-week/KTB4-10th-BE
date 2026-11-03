package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.repository.GuidebookEventChangeNotificationQuery;
import com.ktb10.kgb.content.repository.GuidebookEventChangeNotificationTarget;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.service.NotificationService;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 변경·취소된 행사를 포함한 가이드북 생성자에게 알림을 생성합니다. */
@Service
public class GuidebookEventChangeNotificationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            GuidebookEventChangeNotificationProcessor.class);
    private static final String TITLE = "가이드북 일정에 변경 사항이 있어요";
    private static final String CHANGED_BODY =
            "가이드북에 포함된 행사의 일정이 변경되었어요. 확인해 주세요.";
    private static final String CANCELLED_BODY =
            "가이드북에 포함된 행사가 취소되었어요. 확인해 주세요.";
    private static final String OCCURRENCE_KEY_FORMAT = "guidebook-event-change:%d";

    private final TourApiContentChangeOccurrenceRepository occurrenceRepository;
    private final GuidebookEventChangeCandidateSelector candidateSelector;
    private final GuidebookEventChangeNotificationQuery notificationQuery;
    private final NotificationService notificationService;
    private final GuidebookEventChangeNotificationMetrics metrics;

    public GuidebookEventChangeNotificationProcessor(
            TourApiContentChangeOccurrenceRepository occurrenceRepository,
            GuidebookEventChangeCandidateSelector candidateSelector,
            GuidebookEventChangeNotificationQuery notificationQuery,
            NotificationService notificationService,
            GuidebookEventChangeNotificationMetrics metrics) {
        this.occurrenceRepository = occurrenceRepository;
        this.candidateSelector = candidateSelector;
        this.notificationQuery = notificationQuery;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    public ProcessingResult process(ContentChangesDetectedEvent event) {
        Map<String, GuidebookEventChangeCandidate> candidates = candidateSelector.select(
                        occurrenceRepository.findAllByRunId(event.runId()))
                .stream()
                .collect(Collectors.toMap(
                        GuidebookEventChangeCandidate::sourceContentId,
                        Function.identity()));
        if (candidates.isEmpty()) {
            return new ProcessingResult(0, 0, 0, 0);
        }

        int targets = 0;
        int created = 0;
        int duplicate = 0;
        int failed = 0;
        for (GuidebookEventChangeNotificationTarget target
                : notificationQuery.findTargets(candidates.keySet())) {
            targets++;
            GuidebookEventChangeCandidate candidate = candidates.get(target.sourceContentId());
            try {
                if (createNotification(target, candidate)) {
                    created++;
                    metrics.recordCreated();
                } else {
                    duplicate++;
                    metrics.recordDuplicate();
                }
            } catch (RuntimeException exception) {
                failed++;
                metrics.recordFailed();
                LOGGER.error(
                        "가이드북 포함 행사 변경 알림 생성에 실패했습니다. "
                                + "memberId={}, guidebookId={}, contentId={}",
                        target.memberId(),
                        target.guidebookId(),
                        target.sourceContentId(),
                        exception);
            }
        }
        metrics.recordTargets(targets);
        LOGGER.info(
                "가이드북 포함 행사 변경 알림 처리를 완료했습니다. "
                        + "runId={}, syncDate={}, targets={}, created={}, duplicate={}, failed={}",
                event.runId(),
                event.syncDate(),
                targets,
                created,
                duplicate,
                failed);
        return new ProcessingResult(targets, created, duplicate, failed);
    }

    private boolean createNotification(
            GuidebookEventChangeNotificationTarget target,
            GuidebookEventChangeCandidate candidate) {
        NotificationType type = candidate.cancelled()
                ? NotificationType.GUIDEBOOK_EVENT_CANCELLED
                : NotificationType.GUIDEBOOK_EVENT_CHANGED;
        String body = candidate.cancelled() ? CANCELLED_BODY : CHANGED_BODY;
        return notificationService.createOnce(
                target.memberId(),
                type,
                TITLE,
                body,
                NotificationReferenceType.GUIDEBOOK,
                target.guidebookId().toString(),
                OCCURRENCE_KEY_FORMAT.formatted(candidate.occurrenceId()));
    }

    public record ProcessingResult(int targets, int created, int duplicate, int failed) {
    }
}
