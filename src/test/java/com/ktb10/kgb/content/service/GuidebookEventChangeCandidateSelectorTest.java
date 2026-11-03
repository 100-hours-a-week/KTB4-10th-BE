package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.content.tourapi.ContentChangedField;
import com.ktb10.kgb.content.tourapi.ContentLifecycleChange;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrence;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuidebookEventChangeCandidateSelectorTest {

    private static final UUID RUN_ID =
            UUID.fromString("c783905d-904f-41b9-8016-cb8242533cc8");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 0, 10);

    private final GuidebookEventChangeCandidateSelector selector =
            new GuidebookEventChangeCandidateSelector();

    @Test
    void selectsDateChangesAndInactivationOnly() {
        List<GuidebookEventChangeCandidate> candidates = selector.select(List.of(
                occurrence(1L, "changed", Set.of(ContentChangedField.START_DATE),
                        ContentLifecycleChange.NONE),
                occurrence(2L, "cancelled", Set.of(ContentChangedField.STATUS),
                        ContentLifecycleChange.INACTIVATED),
                occurrence(3L, "description", Set.of(ContentChangedField.DESCRIPTION),
                        ContentLifecycleChange.NONE),
                occurrence(4L, "new-event", Set.of(ContentChangedField.START_DATE),
                        ContentLifecycleChange.NEW)));

        assertThat(candidates).containsExactly(
                new GuidebookEventChangeCandidate(1L, "changed", false),
                new GuidebookEventChangeCandidate(2L, "cancelled", true));
    }

    private TourApiContentChangeOccurrence occurrence(
            Long id,
            String sourceContentId,
            Set<ContentChangedField> fields,
            ContentLifecycleChange lifecycleChange) {
        return new TourApiContentChangeOccurrence(
                id,
                RUN_ID,
                sourceContentId,
                fields,
                lifecycleChange,
                null,
                null,
                NOW,
                NOW);
    }
}
