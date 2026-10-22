package com.ktb10.kgb.content.tourapi;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 정해진 시각과 애플리케이션 기동 시점에 당일 동기화를 보장합니다. */
@Component
@ConditionalOnProperty(prefix = "tour-api.scheduler", name = "enabled", havingValue = "true")
public class TourApiSyncScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TourApiSyncRunner runner;
    private final Clock clock;

    public TourApiSyncScheduler(TourApiSyncRunner runner, Clock clock) {
        this.runner = runner;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedRun() {
        runToday();
    }

    @Scheduled(
            cron = "${tour-api.scheduler.cron:0 1 0 * * *}",
            zone = "Asia/Seoul")
    public void runScheduled() {
        runToday();
    }

    @Scheduled(
            fixedDelayString = "${tour-api.scheduler.recovery-interval-millis:300000}",
            initialDelayString = "${tour-api.scheduler.recovery-interval-millis:300000}")
    public void recoverStaleRun() {
        runner.recoverStaleIfDue(today());
    }

    private void runToday() {
        runner.runIfDue(today());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL));
    }
}
