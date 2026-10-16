package com.ktb10.kgb.content.event;

import com.ktb10.kgb.content.service.InterestedContentChangeNotificationMetrics;
import com.ktb10.kgb.content.service.InterestedContentChangeNotificationProcessor;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** TourAPI 스케줄러가 기다리지 않도록 변경 알림 작업을 전용 큐에 등록합니다. */
@Component
public class InterestedContentChangeNotificationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            InterestedContentChangeNotificationListener.class);

    private final Executor executor;
    private final InterestedContentChangeNotificationProcessor processor;
    private final InterestedContentChangeNotificationMetrics metrics;

    public InterestedContentChangeNotificationListener(
            @Qualifier("contentNotificationTaskExecutor") Executor executor,
            InterestedContentChangeNotificationProcessor processor,
            InterestedContentChangeNotificationMetrics metrics) {
        this.executor = executor;
        this.processor = processor;
        this.metrics = metrics;
    }

    @EventListener
    public void handle(InterestedContentChangesDetectedEvent event) {
        try {
            executor.execute(() -> process(event));
        } catch (RejectedExecutionException exception) {
            metrics.recordQueueRejected();
            LOGGER.warn(
                    "관심 행사 변경 알림 큐가 가득 차 작업을 등록하지 못했습니다. "
                            + "syncDate={}, candidates={}",
                    event.syncDate(),
                    event.candidates().size());
        }
    }

    private void process(InterestedContentChangesDetectedEvent event) {
        try {
            processor.process(event);
        } catch (RuntimeException exception) {
            metrics.recordProcessingFailed();
            LOGGER.error(
                    "관심 행사 변경 알림 비동기 처리에 실패했습니다. syncDate={}, candidates={}",
                    event.syncDate(),
                    event.candidates().size(),
                    exception);
        }
    }
}
