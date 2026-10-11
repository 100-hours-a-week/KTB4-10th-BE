package com.ktb10.kgb.content.tourapi;

/** TourAPI 증분 동기화의 DB 반영 결과입니다. */
public record ImportSummary(
        int sourceContents,
        int importedContents,
        int skippedMissingCoordinates,
        int skippedMissingRegionMapping,
        int sourceEvents,
        int importedEvents,
        int skippedInvalidEvents) {
}
