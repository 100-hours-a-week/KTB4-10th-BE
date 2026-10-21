package com.ktb10.kgb.content.tourapi;

import java.util.List;

/** TourAPI 증분 동기화의 DB 반영 결과입니다. */
public record ImportSummary(
        int sourceContents,
        int importedContents,
        int skippedMissingCoordinates,
        int skippedMissingRegionMapping,
        int inactiveContents,
        int sourceEvents,
        int importedEvents,
        int skippedInvalidEvents,
        List<ContentChangeSet> changeSets) {

    public ImportSummary {
        changeSets = List.copyOf(changeSets);
    }

    public ImportSummary(
            int sourceContents,
            int importedContents,
            int skippedMissingCoordinates,
            int skippedMissingRegionMapping,
            int inactiveContents,
            int sourceEvents,
            int importedEvents,
            int skippedInvalidEvents) {
        this(
                sourceContents,
                importedContents,
                skippedMissingCoordinates,
                skippedMissingRegionMapping,
                inactiveContents,
                sourceEvents,
                importedEvents,
                skippedInvalidEvents,
                List.of());
    }

    public ImportSummary withChangeSets(List<ContentChangeSet> changes) {
        return new ImportSummary(
                sourceContents,
                importedContents,
                skippedMissingCoordinates,
                skippedMissingRegionMapping,
                inactiveContents,
                sourceEvents,
                importedEvents,
                skippedInvalidEvents,
                changes);
    }
}
