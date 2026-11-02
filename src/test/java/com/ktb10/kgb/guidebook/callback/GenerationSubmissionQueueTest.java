package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerationSubmissionQueueTest {

    private GenerationCallbackTransactionService transactions;
    private GenerationCallbackSubmissionService submissions;
    private GenerationSubmissionQueue queue;

    @BeforeEach
    void setUp() {
        transactions = mock(GenerationCallbackTransactionService.class);
        submissions = mock(GenerationCallbackSubmissionService.class);
        queue = new GenerationSubmissionQueue(transactions, submissions, 3, new SimpleMeterRegistry());
        when(transactions.prepare(any())).thenAnswer(invocation ->
                prepared(invocation.getArgument(0)));
    }

    @AfterEach
    void close() {
        queue.close();
    }

    @Test
    void onlyThreeStartAndCallbackReleasesOneSlotInFifoOrder() {
        for (long id = 1; id <= 200; id++) {
            queue.submit(id);
        }
        verify(submissions, timeout(3000).times(3)).submitPrepared(any());
        verify(transactions, never()).prepare(4L);

        queue.finished(finished("2-0"));

        verify(submissions, timeout(3000)).submitPrepared(prepared(4L));
        verify(transactions, never()).prepare(5L);
        // 같은 콜백이나 과거 회차 이벤트가 슬롯을 두 번 반환하지 않는다.
        queue.finished(finished("2-0"));
        queue.finished(finished("1-9"));
        verify(transactions, never()).prepare(5L);
    }

    @Test
    void enqueueReturnsWhileHttpIsStillBlocked() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(submissions).submitPrepared(any());

        try {
            queue.submit(1L);
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            queue.submit(1L);
            verify(transactions).prepare(1L);
        } finally {
            release.countDown();
        }
    }

    @Test
    void fastCallbackBeforeAdmissionResponseCanStartNextJob() {
        queue.close();
        queue = new GenerationSubmissionQueue(transactions, submissions, 1, new SimpleMeterRegistry());
        doAnswer(invocation -> {
            var prepared = (GenerationCallbackTransactionService.Prepared) invocation.getArgument(0);
            queue.finished(finished(prepared.requestId()));
            return null;
        }).when(submissions).submitPrepared(any());

        queue.submit(1L);
        queue.submit(2L);

        verify(submissions, timeout(3000)).submitPrepared(prepared(2L));
    }

    @Test
    void alreadyStartedOrTerminalJobDoesNotConsumeSlot() {
        queue.close();
        queue = new GenerationSubmissionQueue(transactions, submissions, 1, new SimpleMeterRegistry());
        when(transactions.prepare(1L)).thenReturn(null);

        queue.submit(1L);
        queue.submit(2L);

        verify(submissions, timeout(3000)).submitPrepared(prepared(2L));
    }

    @Test
    void uncertainAdmissionRetainsSlotAndDoesNotDispatchNextJob() {
        queue.close();
        queue = new GenerationSubmissionQueue(transactions, submissions, 1, new SimpleMeterRegistry());
        org.mockito.Mockito.doThrow(new IllegalStateException("accepted write failed"))
                .when(submissions).submitPrepared(prepared(1L));
        queue.submit(1L);
        queue.submit(2L);

        verify(submissions, timeout(3000)).submitPrepared(prepared(1L));
        verify(transactions, never()).prepare(2L);
    }

    private GenerationCallbackTransactionService.SubmissionFinished finished(String requestId) {
        return new GenerationCallbackTransactionService.SubmissionFinished(requestId);
    }

    private GenerationCallbackTransactionService.Prepared prepared(Long jobId) {
        return new GenerationCallbackTransactionService.Prepared(
                jobId, 0, Instant.parse("2026-11-01T00:15:00Z"), null, null, null);
    }
}
