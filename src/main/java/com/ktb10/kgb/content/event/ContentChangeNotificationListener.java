package com.ktb10.kgb.content.event;

import com.ktb10.kgb.common.observability.NotificationCreationBatchMetrics;
import com.ktb10.kgb.common.observability.NotificationCreationProducer;
import com.ktb10.kgb.content.service.ContentChangeNotificationBatchMetrics;
import com.ktb10.kgb.content.service.GuidebookEventChangeNotificationProcessor;
import com.ktb10.kgb.content.service.InterestedContentChangeNotificationProcessor;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 일일 행사 변경 알림 작업을 전용 제한 큐에서 실행합니다. */
@Component
public class ContentChangeNotificationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ContentChangeNotificationListener.class);

    private final Executor executor;
    private final InterestedContentChangeNotificationProcessor interestedProcessor;
    private final GuidebookEventChangeNotificationProcessor guidebookProcessor;
    private final ContentChangeNotificationBatchMetrics metrics;
    private final NotificationCreationBatchMetrics creationMetrics;

    public ContentChangeNotificationListener(
            @Qualifier("contentNotificationTaskExecutor") Executor executor,
            InterestedContentChangeNotificationProcessor interestedProcessor,
            GuidebookEventChangeNotificationProcessor guidebookProcessor,
            ContentChangeNotificationBatchMetrics metrics,
            NotificationCreationBatchMetrics creationMetrics) {
        this.executor = executor;
        this.interestedProcessor = interestedProcessor;
        this.guidebookProcessor = guidebookProcessor;
        this.metrics = metrics;
        this.creationMetrics = creationMetrics;
    }

    @EventListener
    public void handle(ContentChangesDetectedEvent event) {
        try {
            executor.execute(() -> process(event));
        } catch (RejectedExecutionException exception) {
            metrics.recordQueueRejected();
            LOGGER.warn(
                    "행사 변경 알림 큐가 가득 차 작업을 등록하지 못했습니다. "
                            + "phase={}, runId={}, syncDate={}, occurrences={}",
                    event.phase().tagValue(),
                    event.runId(),
                    event.syncDate(),
                    event.occurrenceCount());
        }
    }

    private void process(ContentChangesDetectedEvent event) {
        long startedAt = System.nanoTime();
        processInterestedEvents(event);
        processGuidebookEvents(event);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        metrics.recordDuration(elapsed);
        LOGGER.info(
                "일일 행사 변경 알림 배치를 완료했습니다. "
                        + "phase={}, runId={}, syncDate={}, occurrences={}, durationMillis={}",
                event.phase().tagValue(),
                event.runId(),
                event.syncDate(),
                event.occurrenceCount(),
                elapsed.toMillis());
    }

    private void processInterestedEvents(ContentChangesDetectedEvent event) {
        long startedAt = System.nanoTime();
        try {
            InterestedContentChangeNotificationProcessor.ProcessingResult result =
                    interestedProcessor.process(event);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            creationMetrics.record(
                    NotificationCreationProducer.INTERESTED_EVENT_CHANGE,
                    event.phase(),
                    result.targets(),
                    result.created(),
                    result.duplicate(),
                    result.failed(),
                    elapsed);
            logSummary(
                    NotificationCreationProducer.INTERESTED_EVENT_CHANGE,
                    event,
                    result.targets(),
                    result.created(),
                    result.duplicate(),
                    result.failed(),
                    elapsed);
        } catch (RuntimeException exception) {
            metrics.recordInterestedProcessingFailed();
            creationMetrics.record(
                    NotificationCreationProducer.INTERESTED_EVENT_CHANGE,
                    event.phase(),
                    0,
                    0,
                    0,
                    1,
                    Duration.ofNanos(System.nanoTime() - startedAt));
            LOGGER.error(
                    "관심 행사 변경 알림 처리에 실패했습니다. "
                            + "phase={}, runId={}, syncDate={}",
                    event.phase().tagValue(),
                    event.runId(),
                    event.syncDate(),
                    exception);
        }
    }

    private void processGuidebookEvents(ContentChangesDetectedEvent event) {
        long startedAt = System.nanoTime();
        try {
            GuidebookEventChangeNotificationProcessor.ProcessingResult result =
                    guidebookProcessor.process(event);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            creationMetrics.record(
                    NotificationCreationProducer.GUIDEBOOK_EVENT_CHANGE,
                    event.phase(),
                    result.targets(),
                    result.created(),
                    result.duplicate(),
                    result.failed(),
                    elapsed);
            logSummary(
                    NotificationCreationProducer.GUIDEBOOK_EVENT_CHANGE,
                    event,
                    result.targets(),
                    result.created(),
                    result.duplicate(),
                    result.failed(),
                    elapsed);
        } catch (RuntimeException exception) {
            metrics.recordGuidebookProcessingFailed();
            creationMetrics.record(
                    NotificationCreationProducer.GUIDEBOOK_EVENT_CHANGE,
                    event.phase(),
                    0,
                    0,
                    0,
                    1,
                    Duration.ofNanos(System.nanoTime() - startedAt));
            LOGGER.error(
                    "가이드북 포함 행사 변경 알림 처리에 실패했습니다. "
                            + "phase={}, runId={}, syncDate={}",
                    event.phase().tagValue(),
                    event.runId(),
                    event.syncDate(),
                    exception);
        }
    }

    private void logSummary(
            NotificationCreationProducer producer,
            ContentChangesDetectedEvent event,
            int targets,
            int created,
            int duplicate,
            int failed,
            Duration elapsed) {
        LOGGER.info(
                "행사 변경 알림 생산자 처리를 완료했습니다. "
                        + "producer={}, phase={}, syncDate={}, target={}, created={}, "
                        + "duplicate={}, failed={}, durationMillis={}",
                producer.tagValue(),
                event.phase().tagValue(),
                event.syncDate(),
                targets,
                created,
                duplicate,
                failed,
                elapsed.toMillis());
    }
}
