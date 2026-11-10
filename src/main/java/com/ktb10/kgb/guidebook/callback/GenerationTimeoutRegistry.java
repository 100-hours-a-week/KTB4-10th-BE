package com.ktb10.kgb.guidebook.callback;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 시도별 단발 타이머입니다. 애플리케이션 시작 시 DB의 진행 중 시도도 다시 등록합니다. */
@Component
public class GenerationTimeoutRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationTimeoutRegistry.class);
    private final ConcurrentMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final GenerationTimerScheduler scheduler;
    private final GenerationCallbackTransactionService transactions;

    public GenerationTimeoutRegistry(
            GenerationTimerScheduler scheduler,
            GenerationCallbackTransactionService transactions) {
        this.scheduler = scheduler;
        this.transactions = transactions;
    }

    public void register(GenerationCallbackTransactionService.Prepared prepared) {
        register(prepared.jobId(), prepared.attempt(), prepared.deadline());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void register(GenerationCallbackTransactionService.AttemptStarted event) {
        register(event.jobId(), event.attempt(), event.deadline());
    }

    public void register(Long jobId, int attempt, Instant deadline) {
        String requestId = jobId + "-" + attempt;
        timers.computeIfAbsent(requestId, ignored -> scheduler.schedule(() -> {
            try {
                transactions.expire(jobId, attempt);
            } catch (RuntimeException exception) {
                LOGGER.error("생성 타임아웃 처리에 실패했습니다. requestId={}",
                        requestId, exception);
            } finally {
                timers.remove(requestId);
            }
        }, deadline));
    }

    public void cancel(String requestId) {
        ScheduledFuture<?> future = timers.remove(requestId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void close() {
        timers.values().forEach(future -> future.cancel(false));
        timers.clear();
    }
}
