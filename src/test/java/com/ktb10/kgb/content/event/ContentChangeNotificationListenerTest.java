package com.ktb10.kgb.content.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.common.observability.NotificationCreationBatchMetrics;
import com.ktb10.kgb.content.service.ContentChangeNotificationBatchMetrics;
import com.ktb10.kgb.content.service.GuidebookEventChangeNotificationProcessor;
import com.ktb10.kgb.content.service.InterestedContentChangeNotificationProcessor;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContentChangeNotificationListenerTest {

    @Test
    void delegatesBothProcessorsToDedicatedExecutor() {
        Executor executor = mock(Executor.class);
        InterestedContentChangeNotificationProcessor interestedProcessor =
                mock(InterestedContentChangeNotificationProcessor.class);
        GuidebookEventChangeNotificationProcessor guidebookProcessor =
                mock(GuidebookEventChangeNotificationProcessor.class);
        ContentChangeNotificationBatchMetrics metrics =
                mock(ContentChangeNotificationBatchMetrics.class);
        NotificationCreationBatchMetrics creationMetrics =
                mock(NotificationCreationBatchMetrics.class);
        ContentChangeNotificationListener listener = new ContentChangeNotificationListener(
                executor,
                interestedProcessor,
                guidebookProcessor,
                metrics,
                creationMetrics);
        ContentChangesDetectedEvent event = event();
        org.mockito.BDDMockito.given(interestedProcessor.process(event))
                .willReturn(new InterestedContentChangeNotificationProcessor.ProcessingResult(
                        0, 0, 0, 0));
        org.mockito.BDDMockito.given(guidebookProcessor.process(event))
                .willReturn(new GuidebookEventChangeNotificationProcessor.ProcessingResult(
                        0, 0, 0, 0));
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        listener.handle(event);

        verify(executor).execute(taskCaptor.capture());
        verify(interestedProcessor, never()).process(event);
        verify(guidebookProcessor, never()).process(event);

        taskCaptor.getValue().run();

        verify(interestedProcessor).process(event);
        verify(guidebookProcessor).process(event);
    }

    @Test
    void continuesGuidebookProcessingWhenInterestedProcessorFails() {
        Executor executor = Runnable::run;
        InterestedContentChangeNotificationProcessor interestedProcessor =
                mock(InterestedContentChangeNotificationProcessor.class);
        GuidebookEventChangeNotificationProcessor guidebookProcessor =
                mock(GuidebookEventChangeNotificationProcessor.class);
        ContentChangeNotificationBatchMetrics metrics =
                mock(ContentChangeNotificationBatchMetrics.class);
        NotificationCreationBatchMetrics creationMetrics =
                mock(NotificationCreationBatchMetrics.class);
        ContentChangeNotificationListener listener = new ContentChangeNotificationListener(
                executor,
                interestedProcessor,
                guidebookProcessor,
                metrics,
                creationMetrics);
        ContentChangesDetectedEvent event = event();
        doThrow(new IllegalStateException("관심 행사 처리 실패"))
                .when(interestedProcessor)
                .process(event);
        org.mockito.BDDMockito.given(guidebookProcessor.process(event))
                .willReturn(new GuidebookEventChangeNotificationProcessor.ProcessingResult(
                        0, 0, 0, 0));

        listener.handle(event);

        verify(metrics).recordInterestedProcessingFailed();
        verify(guidebookProcessor).process(event);
    }

    @Test
    void doesNotPropagateRejectedTaskToSchedulerThread() {
        Executor executor = mock(Executor.class);
        InterestedContentChangeNotificationProcessor interestedProcessor =
                mock(InterestedContentChangeNotificationProcessor.class);
        GuidebookEventChangeNotificationProcessor guidebookProcessor =
                mock(GuidebookEventChangeNotificationProcessor.class);
        ContentChangeNotificationBatchMetrics metrics =
                mock(ContentChangeNotificationBatchMetrics.class);
        NotificationCreationBatchMetrics creationMetrics =
                mock(NotificationCreationBatchMetrics.class);
        ContentChangeNotificationListener listener = new ContentChangeNotificationListener(
                executor,
                interestedProcessor,
                guidebookProcessor,
                metrics,
                creationMetrics);
        doThrow(new RejectedExecutionException("큐가 가득 참"))
                .when(executor)
                .execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        listener.handle(event());

        verify(metrics).recordQueueRejected();
        verify(interestedProcessor, never()).process(org.mockito.ArgumentMatchers.any());
        verify(guidebookProcessor, never()).process(org.mockito.ArgumentMatchers.any());
    }

    private ContentChangesDetectedEvent event() {
        return new ContentChangesDetectedEvent(
                UUID.fromString("7e85cb87-3d65-4dc6-832e-a5303030af31"),
                LocalDate.of(2026, 10, 15),
                1);
    }
}
