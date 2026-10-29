package com.ktb10.kgb.guidebook.callback;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/** TaskScheduler 빈을 노출하지 않아 기존 주기 작업 실행기와 타이머를 분리합니다. */
@Component
public class GenerationTimerScheduler {

    private final ThreadPoolTaskScheduler scheduler;

    public GenerationTimerScheduler() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("generation-timeout-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
    }

    public ScheduledFuture<?> schedule(Runnable task, Instant deadline) {
        return scheduler.schedule(task, deadline);
    }

    @PreDestroy
    public void close() {
        scheduler.shutdown();
    }
}
