package com.ktb10.kgb.content.event;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** 지연된 TourAPI 동기화가 성공했음을 일일 후속 작업에 알립니다. */
public record TourApiSyncCompletedEvent(UUID runId, LocalDate syncDate) {

    public TourApiSyncCompletedEvent {
        Objects.requireNonNull(runId, "동기화 실행 ID는 필수입니다.");
        Objects.requireNonNull(syncDate, "동기화 기준일은 필수입니다.");
    }
}
