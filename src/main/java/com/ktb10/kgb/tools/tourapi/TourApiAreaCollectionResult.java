package com.ktb10.kgb.tools.tourapi;

import java.time.Duration;

/** TourAPI 전국 목록 한 번 수집의 검증·저장·적재 결과입니다. */
public record TourApiAreaCollectionResult(
        int totalCount,
        int responseBytes,
        ImportSummary importSummary,
        int attempts,
        Duration elapsed,
        String storedAt) {

    public String toDisplayText() {
        return
                """
                TourAPI 전국 목록 수집 완료
                - 전체 건수: %d건
                - 응답 크기: %d bytes
                - 요청 횟수: %d회
                - 저장 위치: %s
                - 전체 소요 시간: %dms
                %s
                """
                        .formatted(
                                totalCount,
                                responseBytes,
                                attempts,
                                storedAt,
                                elapsed.toMillis(),
                                importSummary.toDisplayText());
    }
}
