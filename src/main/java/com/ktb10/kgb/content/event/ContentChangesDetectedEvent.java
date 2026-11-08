package com.ktb10.kgb.content.event;

import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** 최초 실행 또는 제한 재실행의 일일 행사 변경 알림 처리를 요청합니다. */
public record ContentChangesDetectedEvent(
        UUID runId,
        LocalDate syncDate,
        int occurrenceCount,
        NotificationCreationPhase phase) {

    public ContentChangesDetectedEvent(UUID runId, LocalDate syncDate, int occurrenceCount) {
        this(runId, syncDate, occurrenceCount, NotificationCreationPhase.INITIAL);
    }

    public ContentChangesDetectedEvent {
        Objects.requireNonNull(runId, "동기화 실행 ID는 필수입니다.");
        Objects.requireNonNull(syncDate, "동기화 기준일은 필수입니다.");
        Objects.requireNonNull(phase, "알림 생성 단계는 필수입니다.");
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException("변경 이력 건수는 1 이상이어야 합니다.");
        }
    }
}
