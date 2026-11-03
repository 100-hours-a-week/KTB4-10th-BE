package com.ktb10.kgb.content.event;

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

/** 오전 9시 행사 변경 알림 작업을 전용 제한 큐에서 실행합니다. */
@Component
public class ContentChangeNotificationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ContentChangeNotificationListener.class);

    private final Executor executor;
    private final InterestedContentChangeNotificationProcessor interestedProcessor;
    private final GuidebookEventChangeNotificationProcessor guidebookProcessor;
    private final ContentChangeNotificationBatchMetrics metrics;

    public ContentChangeNotificationListener(
            @Qualifier("contentNotificationTaskExecutor") Executor executor,
            InterestedContentChangeNotificationProcessor interestedProcessor,
            GuidebookEventChangeNotificationProcessor guidebookProcessor,
            ContentChangeNotificationBatchMetrics metrics) {
        this.executor = executor;
        this.interestedProcessor = interestedProcessor;
        this.guidebookProcessor = guidebookProcessor;
        this.metrics = metrics;
    }

    @EventListener
    public void handle(ContentChangesDetectedEvent event) {
        try {
            executor.execute(() -> process(event));
        } catch (RejectedExecutionException exception) {
            metrics.recordQueueRejected();
            LOGGER.warn(
                    "행사 변경 알림 큐가 가득 차 작업을 등록하지 못했습니다. "
                            + "runId={}, syncDate={}, occurrences={}",
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
                "오전 9시 행사 변경 알림 배치를 완료했습니다. "
                        + "runId={}, syncDate={}, occurrences={}, durationMillis={}",
                event.runId(),
                event.syncDate(),
                event.occurrenceCount(),
                elapsed.toMillis());
    }

    private void processInterestedEvents(ContentChangesDetectedEvent event) {
        try {
            interestedProcessor.process(event);
        } catch (RuntimeException exception) {
            metrics.recordInterestedProcessingFailed();
            LOGGER.error(
                    "관심 행사 변경 알림 처리에 실패했습니다. "
                            + "runId={}, syncDate={}",
                    event.runId(),
                    event.syncDate(),
                    exception);
        }
    }

    private void processGuidebookEvents(ContentChangesDetectedEvent event) {
        try {
            guidebookProcessor.process(event);
        } catch (RuntimeException exception) {
            metrics.recordGuidebookProcessingFailed();
            LOGGER.error(
                    "가이드북 포함 행사 변경 알림 처리에 실패했습니다. "
                            + "runId={}, syncDate={}",
                    event.runId(),
                    event.syncDate(),
                    exception);
        }
    }
}
