package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentChangeSetTest {

    @Test
    void keepsChangedFieldsWithoutPrecomputingProcessingGroups() {
        ContentChangeSet changeSet = ContentChangeSet.of(
                "100",
                Set.of(
                        ContentChangedField.DESCRIPTION,
                        ContentChangedField.EVENT_PLACE,
                        ContentChangedField.LOCATION),
                ContentLifecycleChange.NONE,
                new TourApiContentLocation(37.4, 127.1),
                new TourApiContentLocation(37.5, 127.2));

        assertThat(changeSet.changedFields()).containsExactlyInAnyOrder(
                ContentChangedField.DESCRIPTION,
                ContentChangedField.EVENT_PLACE,
                ContentChangedField.LOCATION);
    }

    @Test
    void mergesListAndDetailChangesForSameContent() {
        ContentChangeSet listChange = ContentChangeSet.of(
                "100",
                Set.of(ContentChangedField.TITLE),
                ContentLifecycleChange.NONE,
                new TourApiContentLocation(37.4, 127.1),
                new TourApiContentLocation(37.5, 127.2));
        ContentChangeSet detailChange = ContentChangeSet.of(
                "100",
                Set.of(ContentChangedField.EVENT_PLACE),
                ContentLifecycleChange.NONE,
                null,
                new TourApiContentLocation(37.5, 127.2));

        List<ContentChangeSet> merged = ContentChangeSet.mergeAll(
                List.of(listChange, detailChange));

        assertThat(merged).singleElement().satisfies(change -> {
            assertThat(change.changedFields()).containsExactlyInAnyOrder(
                    ContentChangedField.TITLE,
                    ContentChangedField.EVENT_PLACE);
            assertThat(change.previousLocation()).isEqualTo(listChange.previousLocation());
            assertThat(change.currentLocation()).isEqualTo(detailChange.currentLocation());
        });
    }
}
