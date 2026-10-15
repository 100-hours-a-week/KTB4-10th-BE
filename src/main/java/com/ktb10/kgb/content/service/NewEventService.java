package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.dto.NewEventItemResponse;
import com.ktb10.kgb.content.dto.NewEventListResponse;
import com.ktb10.kgb.content.repository.NewEventData;
import com.ktb10.kgb.content.repository.NewEventQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일자별 TourAPI 신규 행사 목록을 제공합니다. */
@Service
public class NewEventService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final NewEventQuery newEventQuery;
    private final Clock clock;

    public NewEventService(NewEventQuery newEventQuery, Clock clock) {
        this.newEventQuery = newEventQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NewEventListResponse getNewEvents(LocalDate requestedDate, int page, int size) {
        LocalDate date = requestedDate == null
                ? LocalDate.now(clock.withZone(SEOUL))
                : requestedDate;
        LocalDateTime startInclusive = date.atStartOfDay();
        LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();
        long totalItems = newEventQuery.countBySourceCreatedAt(
                startInclusive,
                endExclusive);
        List<NewEventData> data = newEventQuery.findBySourceCreatedAt(
                startInclusive,
                endExclusive,
                size,
                (long) (page - 1) * size);
        List<NewEventItemResponse> items = data.stream()
                .map(NewEventItemResponse::from)
                .toList();
        int totalPages = totalItems == 0
                ? 0
                : Math.toIntExact((totalItems + size - 1) / size);
        return new NewEventListResponse(
                date,
                items,
                page,
                size,
                totalItems,
                totalPages);
    }
}
