package com.ktb10.kgb.member.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Asia/Seoul 기준 오전 9시에 다음 날 시작하는 여행 알림을 생성합니다. */
@Component
public class TripStartingNotificationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TripStartingNotificationScheduler.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final TripStartingNotificationService notificationService;
    private final Clock clock;

    public TripStartingNotificationScheduler(
            TripStartingNotificationService notificationService,
            Clock clock) {
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${notification.trip-starting.cron}",
            zone = "Asia/Seoul")
    public void createTomorrowNotifications() {
        LocalDate startDate = LocalDate.now(clock.withZone(SEOUL_ZONE)).plusDays(1);
        TripStartingNotificationSummary summary =
                notificationService.createForStartDate(startDate);
        LOGGER.info(
                "여행 일정 임박 알림 생성을 완료했습니다. "
                        + "startDate={}, created={}, duplicated={}, failed={}",
                startDate,
                summary.created(),
                summary.duplicated(),
                summary.failed());
    }
}
