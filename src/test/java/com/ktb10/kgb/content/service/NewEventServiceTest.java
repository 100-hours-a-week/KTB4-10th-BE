package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.repository.NewEventData;
import com.ktb10.kgb.content.repository.NewEventQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewEventServiceTest {

    @Mock
    private NewEventQuery newEventQuery;

    @Test
    void usesSeoulTodayAndReturnsRequestedPage() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-14T15:30:00Z"),
                ZoneOffset.UTC);
        NewEventService service = new NewEventService(newEventQuery, clock);
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 16, 0, 0);
        NewEventData event = new NewEventData(
                "event-1",
                "가을 축제",
                "서울특별시 중구",
                "https://example.com/event.jpg",
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 21),
                LocalDateTime.of(2026, 10, 15, 8, 30));
        when(newEventQuery.countBySourceCreatedAt(start, end)).thenReturn(21L);
        when(newEventQuery.findBySourceCreatedAt(start, end, 10, 10L))
                .thenReturn(List.of(event));

        var response = service.getNewEvents(null, 2, 10);

        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalItems()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().contentId()).isEqualTo("event-1");
        verify(newEventQuery).findBySourceCreatedAt(start, end, 10, 10L);
    }

    @Test
    void returnsEmptyPageWhenNoEventWasCreatedThatDay() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        NewEventService service = new NewEventService(
                newEventQuery,
                Clock.systemUTC());
        when(newEventQuery.findBySourceCreatedAt(start, end, 20, 0L))
                .thenReturn(List.of());

        var response = service.getNewEvents(date, 1, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
