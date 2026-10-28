package com.ktb10.kgb.guidebook.callback;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 시도별 단발 타이머입니다. 재시작 복구·영속 재처리는 의도적으로 제공하지 않습니다. */
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
        timers.computeIfAbsent(prepared.requestId(), ignored -> scheduler.schedule(() -> {
            try {
                transactions.expire(prepared.jobId(), prepared.attempt());
            } catch (RuntimeException exception) {
                LOGGER.error("생성 타임아웃 처리에 실패했습니다. requestId={}",
                        prepared.requestId(), exception);
            } finally {
                timers.remove(prepared.requestId());
            }
        }, prepared.deadline()));
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
