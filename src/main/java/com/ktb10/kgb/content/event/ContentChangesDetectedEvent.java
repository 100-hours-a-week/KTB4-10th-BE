package com.ktb10.kgb.content.event;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** 오전 9시 일일 행사 변경 알림 처리를 요청합니다. */
public record ContentChangesDetectedEvent(
        UUID runId,
        LocalDate syncDate,
        int occurrenceCount) {

    public ContentChangesDetectedEvent {
        Objects.requireNonNull(runId, "동기화 실행 ID는 필수입니다.");
        Objects.requireNonNull(syncDate, "동기화 기준일은 필수입니다.");
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException("변경 이력 건수는 1 이상이어야 합니다.");
        }
    }
}
