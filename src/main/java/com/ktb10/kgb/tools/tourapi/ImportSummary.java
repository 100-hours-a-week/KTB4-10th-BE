package com.ktb10.kgb.tools.tourapi;

/** TourAPI 초기 적재 결과입니다. */
public record ImportSummary(
        int sourceContents,
        int importedContents,
        int skippedMissingCoordinates,
        int skippedMissingRegionMapping,
        int sourceEvents,
        int importedEvents,
        int skippedInvalidEvents) {

    public String toDisplayText() {
        return
                """
                TourAPI 초기 적재 완료
                - 공통 원본: %d건
                - 콘텐츠 upsert: %d건
                - 좌표 누락 제외: %d건
                - 지역 매핑 실패 제외: %d건
                - 행사 원본: %d건
                - 행사 상세 upsert: %d건
                - 잘못된 행사 제외: %d건
                """
                        .formatted(
                sourceContents,
                importedContents,
                skippedMissingCoordinates,
                skippedMissingRegionMapping,
                sourceEvents,
                importedEvents,
                                skippedInvalidEvents);
    }
}
