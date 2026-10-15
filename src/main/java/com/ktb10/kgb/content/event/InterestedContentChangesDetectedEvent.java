package com.ktb10.kgb.content.event;

import com.ktb10.kgb.content.tourapi.InterestedContentChangeCandidate;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** TourAPI 동기화 성공 후 관심 행사 변경 알림 처리를 요청합니다. */
public record InterestedContentChangesDetectedEvent(
        LocalDate syncDate,
        List<InterestedContentChangeCandidate> candidates) {

    public InterestedContentChangesDetectedEvent {
        Objects.requireNonNull(syncDate);
        Objects.requireNonNull(candidates);
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("관심 행사 변경 후보는 비어 있을 수 없습니다.");
        }
    }
}
