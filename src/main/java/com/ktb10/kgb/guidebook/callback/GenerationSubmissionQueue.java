package com.ktb10.kgb.guidebook.callback;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 단일 BE 전용 무제한 메모리 대기열. DB 재조회·재접수·슬롯 유실 복구는 하지 않는다. */
@Component
@Profile({"local", "prod"})
public class GenerationSubmissionQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationSubmissionQueue.class);
    private final Queue<Long> waiting = new ArrayDeque<>();
    private final Set<Long> registered = new HashSet<>();
    private final Map<Long, String> running = new HashMap<>();
    private final GenerationCallbackTransactionService transactions;
    private final GenerationCallbackSubmissionService submissions;
    private final ExecutorService executor;
    private final int concurrency;
    private boolean closed;

    public GenerationSubmissionQueue(
            GenerationCallbackTransactionService transactions,
            GenerationCallbackSubmissionService submissions,
            @Value("${ai.generation.submission-concurrency:3}") int concurrency,
            MeterRegistry metrics) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("AI 접수 동시 실행 한도는 양수여야 합니다.");
        }
        this.transactions = transactions;
        this.submissions = submissions;
        this.concurrency = concurrency;
        this.executor = Executors.newFixedThreadPool(concurrency,
                Thread.ofPlatform().name("generation-submission-", 0).factory());
        Gauge.builder("guidebook.generation.queue.waiting", this, queue -> queue.waitingCount())
                .register(metrics);
        Gauge.builder("guidebook.generation.queue.running", this, queue -> queue.runningCount())
                .register(metrics);
        Gauge.builder("guidebook.generation.queue.limit", () -> concurrency).register(metrics);
    }

    private synchronized int waitingCount() {
        return waiting.size();
    }

    private synchronized int runningCount() {
        return running.size();
    }

    @Transactional(propagation = Propagation.NEVER)
    public synchronized void submit(Long jobId) {
        if (closed) {
            throw new IllegalStateException("AI 접수 큐가 종료됐습니다.");
        }
        if (registered.add(jobId)) {
            waiting.add(jobId);
            dispatch();
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void finished(GenerationCallbackTransactionService.SubmissionFinished event) {
        Long jobId = running.entrySet().stream()
                .filter(entry -> event.requestId().equals(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (jobId != null) {
            release(jobId);
        }
    }

    // 잠금 안에서는 메모리 상태와 실행기 등록만 처리한다. DB·HTTP는 실행기 스레드에서 수행한다.
    private void dispatch() {
        while (!closed && running.size() < concurrency && !waiting.isEmpty()) {
            Long jobId = waiting.remove();
            running.put(jobId, "");
            executor.execute(() -> send(jobId));
        }
    }

    private void send(Long jobId) {
        GenerationCallbackTransactionService.Prepared prepared;
        try {
            prepared = transactions.prepare(jobId);
        } catch (RuntimeException exception) {
            LOGGER.error("AI 접수 준비 실패. 자동 재시도하지 않습니다. jobId={}", jobId, exception);
            release(jobId);
            return;
        }
        if (prepared == null) {
            release(jobId);
            return;
        }
        synchronized (this) {
            running.put(jobId, prepared.requestId());
        }
        try {
            submissions.submitPrepared(prepared);
        } catch (RuntimeException exception) {
            // AI가 이미 접수했을 수 있다. 종료 확인 없이 다음 작업을 보내지 않는다.
            LOGGER.error("AI 접수 결과 불확실. 슬롯을 유지합니다. requestId={}",
                    prepared.requestId(), exception);
        }
    }

    private synchronized void release(Long jobId) {
        running.remove(jobId);
        registered.remove(jobId);
        dispatch();
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        executor.shutdownNow();
    }
}
