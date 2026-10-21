package com.ktb10.kgb.member.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripStartingNotificationSchedulerTest {

    @Mock
    private TripStartingNotificationService notificationService;

    @Test
    void requestsTomorrowBasedOnSeoulDate() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-16T23:30:00Z"),
                ZoneOffset.UTC);
        TripStartingNotificationScheduler scheduler =
                new TripStartingNotificationScheduler(notificationService, clock);
        given(notificationService.createForStartDate(LocalDate.of(2026, 10, 18)))
                .willReturn(new TripStartingNotificationSummary(0, 0, 0));

        scheduler.createTomorrowNotifications();

        verify(notificationService).createForStartDate(LocalDate.of(2026, 10, 18));
    }
}
