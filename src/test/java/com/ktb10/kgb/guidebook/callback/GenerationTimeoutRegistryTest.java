package com.ktb10.kgb.guidebook.callback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenerationTimeoutRegistryTest {

    @Test
    void schedulesOncePerAttemptAndExpiresExpectedAttempt() {
        GenerationTimerScheduler scheduler = mock(GenerationTimerScheduler.class);
        GenerationCallbackTransactionService transactions =
                mock(GenerationCallbackTransactionService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        var prepared = new GenerationCallbackTransactionService.Prepared(
                301L, 1, Instant.parse("2026-10-28T00:15:00Z"), null, null, null);
        when(scheduler.schedule(any(), eq(prepared.deadline()))).thenReturn((ScheduledFuture) future);
        GenerationTimeoutRegistry timers = new GenerationTimeoutRegistry(scheduler, transactions);
        timers.register(prepared);
        timers.register(prepared);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).schedule(task.capture(), eq(prepared.deadline()));
        verify(transactions, never()).expire(301L, 1);
        task.getValue().run();
        verify(transactions).expire(301L, 1);
    }

    @Test
    void cancellationLeavesNoTimerAndDoesNotInterruptRunningWork() {
        GenerationTimerScheduler scheduler = mock(GenerationTimerScheduler.class);
        GenerationCallbackTransactionService transactions =
                mock(GenerationCallbackTransactionService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        var prepared = new GenerationCallbackTransactionService.Prepared(
                301L, 0, Instant.now().plusSeconds(900), null, null, null);
        when(scheduler.schedule(any(), any())).thenReturn((ScheduledFuture) future);
        GenerationTimeoutRegistry timers = new GenerationTimeoutRegistry(scheduler, transactions);
        timers.register(prepared);
        timers.cancel("301-0");
        timers.close();
        verify(future, times(1)).cancel(false);
        verify(transactions, never()).expire(any(), eq(0));
    }
}
