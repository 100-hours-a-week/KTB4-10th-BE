package com.ktb10.kgb.content.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.content.service.InterestedContentChangeNotificationMetrics;
import com.ktb10.kgb.content.service.InterestedContentChangeNotificationProcessor;
import com.ktb10.kgb.content.tourapi.ContentChangedField;
import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidate;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterestedContentChangeNotificationListenerTest {

    @Test
    void delegatesProcessingToDedicatedExecutor() {
        Executor executor = mock(Executor.class);
        InterestedContentChangeNotificationProcessor processor =
                mock(InterestedContentChangeNotificationProcessor.class);
        InterestedContentChangeNotificationMetrics metrics =
                mock(InterestedContentChangeNotificationMetrics.class);
        InterestedContentChangeNotificationListener listener =
                new InterestedContentChangeNotificationListener(executor, processor, metrics);
        InterestedContentChangesDetectedEvent event = event();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        listener.handle(event);

        verify(executor).execute(taskCaptor.capture());
        verify(processor, never()).process(event);

        taskCaptor.getValue().run();

        verify(processor).process(event);
    }

    @Test
    void doesNotPropagateRejectedTaskToSchedulerThread() {
        Executor executor = mock(Executor.class);
        InterestedContentChangeNotificationProcessor processor =
                mock(InterestedContentChangeNotificationProcessor.class);
        InterestedContentChangeNotificationMetrics metrics =
                mock(InterestedContentChangeNotificationMetrics.class);
        InterestedContentChangeNotificationListener listener =
                new InterestedContentChangeNotificationListener(executor, processor, metrics);
        InterestedContentChangesDetectedEvent event = event();
        doThrow(new RejectedExecutionException("큐"))
                .when(executor)
                .execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        listener.handle(event);

        verify(processor, never()).process(event);
        verify(metrics).recordQueueRejected();
    }

    @Test
    void recordsUnexpectedProcessingFailure() {
        Executor executor = Runnable::run;
        InterestedContentChangeNotificationProcessor processor =
                mock(InterestedContentChangeNotificationProcessor.class);
        InterestedContentChangeNotificationMetrics metrics =
                mock(InterestedContentChangeNotificationMetrics.class);
        InterestedContentChangeNotificationListener listener =
                new InterestedContentChangeNotificationListener(executor, processor, metrics);
        InterestedContentChangesDetectedEvent event = event();
        doThrow(new IllegalStateException("조회 실패")).when(processor).process(event);

        listener.handle(event);

        verify(metrics).recordProcessingFailed();
    }

    private InterestedContentChangesDetectedEvent event() {
        return new InterestedContentChangesDetectedEvent(
                LocalDate.of(2026, 10, 15),
                List.of(new InterestedContentChangeCandidate(
                        "100",
                        Set.of(ContentChangedField.DESCRIPTION))));
    }
}
