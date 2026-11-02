package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterestedContentChangeCandidateSelectorTest {

    private final InterestedContentChangeCandidateSelector selector =
            new InterestedContentChangeCandidateSelector();

    @Test
    void selectsOnlyUserMeaningfulFields() {
        TourApiContentChangeOccurrence occurrence = occurrence(
                11L,
                "100",
                Set.of(
                        ContentChangedField.DESCRIPTION,
                        ContentChangedField.START_DATE,
                        ContentChangedField.LOCATION,
                        ContentChangedField.THUMBNAIL_URL),
                ContentLifecycleChange.NONE);

        List<InterestedContentChangeCandidate> candidates = selector.select(List.of(occurrence));

        assertThat(candidates).containsExactly(new InterestedContentChangeCandidate(
                11L,
                "100",
                Set.of(ContentChangedField.DESCRIPTION, ContentChangedField.START_DATE)));
    }

    @Test
    void excludesChangesOnlyUsedForMapOrInternalClassification() {
        TourApiContentChangeOccurrence occurrence = occurrence(
                11L,
                "100",
                Set.of(
                        ContentChangedField.LOCATION,
                        ContentChangedField.THUMBNAIL_URL,
                        ContentChangedField.CLASSIFICATION_CODE_1,
                        ContentChangedField.SOURCE_CONTENT_TYPE_ID),
                ContentLifecycleChange.NONE);

        assertThat(selector.select(List.of(occurrence))).isEmpty();
    }

    @Test
    void excludesNewContentForSeparateDailyDigestPolicy() {
        TourApiContentChangeOccurrence occurrence = occurrence(
                11L,
                "100",
                Set.of(ContentChangedField.TITLE, ContentChangedField.DESCRIPTION),
                ContentLifecycleChange.NEW);

        assertThat(selector.select(List.of(occurrence))).isEmpty();
    }

    @Test
    void excludesChangeSetWithoutChangedFields() {
        TourApiContentChangeOccurrence occurrence = occurrence(
                11L,
                "100",
                Set.of(),
                ContentLifecycleChange.NONE);

        assertThat(selector.select(List.of(occurrence))).isEmpty();
    }

    @Test
    void selectsInactivatedContentAsStatusChange() {
        TourApiContentChangeOccurrence occurrence = occurrence(
                11L,
                "100",
                Set.of(ContentChangedField.STATUS),
                ContentLifecycleChange.INACTIVATED);

        assertThat(selector.select(List.of(occurrence)))
                .containsExactly(new InterestedContentChangeCandidate(
                        11L,
                        "100",
                        Set.of(ContentChangedField.STATUS)));
    }

    private TourApiContentChangeOccurrence occurrence(
            Long occurrenceId,
            String sourceContentId,
            Set<ContentChangedField> fields,
            ContentLifecycleChange lifecycleChange) {
        return new TourApiContentChangeOccurrence(
                occurrenceId,
                UUID.fromString("7e85cb87-3d65-4dc6-832e-a5303030af31"),
                sourceContentId,
                fields,
                lifecycleChange,
                null,
                null,
                java.time.LocalDateTime.of(2026, 10, 30, 0, 0),
                java.time.LocalDateTime.of(2026, 10, 30, 0, 0));
    }
}
