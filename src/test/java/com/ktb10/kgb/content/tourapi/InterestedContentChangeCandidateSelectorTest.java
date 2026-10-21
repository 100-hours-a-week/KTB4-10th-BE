package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterestedContentChangeCandidateSelectorTest {

    private final InterestedContentChangeCandidateSelector selector =
            new InterestedContentChangeCandidateSelector();

    @Test
    void selectsOnlyUserMeaningfulFields() {
        ContentChangeSet changeSet = changeSet(
                "100",
                Set.of(
                        ContentChangedField.DESCRIPTION,
                        ContentChangedField.START_DATE,
                        ContentChangedField.LOCATION,
                        ContentChangedField.THUMBNAIL_URL),
                ContentLifecycleChange.NONE);

        List<InterestedContentChangeCandidate> candidates = selector.select(List.of(changeSet));

        assertThat(candidates).containsExactly(new InterestedContentChangeCandidate(
                "100",
                Set.of(ContentChangedField.DESCRIPTION, ContentChangedField.START_DATE)));
    }

    @Test
    void excludesChangesOnlyUsedForMapOrInternalClassification() {
        ContentChangeSet changeSet = changeSet(
                "100",
                Set.of(
                        ContentChangedField.LOCATION,
                        ContentChangedField.THUMBNAIL_URL,
                        ContentChangedField.CLASSIFICATION_CODE_1,
                        ContentChangedField.SOURCE_CONTENT_TYPE_ID),
                ContentLifecycleChange.NONE);

        assertThat(selector.select(List.of(changeSet))).isEmpty();
    }

    @Test
    void excludesNewContentForSeparateDailyDigestPolicy() {
        ContentChangeSet changeSet = changeSet(
                "100",
                Set.of(ContentChangedField.TITLE, ContentChangedField.DESCRIPTION),
                ContentLifecycleChange.NEW);

        assertThat(selector.select(List.of(changeSet))).isEmpty();
    }

    @Test
    void excludesChangeSetWithoutChangedFields() {
        ContentChangeSet changeSet = changeSet(
                "100",
                Set.of(),
                ContentLifecycleChange.NONE);

        assertThat(selector.select(List.of(changeSet))).isEmpty();
    }

    @Test
    void selectsInactivatedContentAsStatusChange() {
        ContentChangeSet changeSet = changeSet(
                "100",
                Set.of(ContentChangedField.STATUS),
                ContentLifecycleChange.INACTIVATED);

        assertThat(selector.select(List.of(changeSet)))
                .containsExactly(new InterestedContentChangeCandidate(
                        "100",
                        Set.of(ContentChangedField.STATUS)));
    }

    private ContentChangeSet changeSet(
            String sourceContentId,
            Set<ContentChangedField> fields,
            ContentLifecycleChange lifecycleChange) {
        return ContentChangeSet.of(
                sourceContentId,
                fields,
                lifecycleChange,
                null,
                null);
    }
}
