package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.tourapi.ContentChangedField;
import com.ktb10.kgb.content.tourapi.ContentLifecycleChange;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrence;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** 행사 변경 이력에서 기존 가이드북 일정에 영향을 주는 변경만 선별합니다. */
@Component
public class GuidebookEventChangeCandidateSelector {

    public List<GuidebookEventChangeCandidate> select(
            Collection<TourApiContentChangeOccurrence> occurrences) {
        return occurrences.stream()
                .filter(this::affectsGuidebookSchedule)
                .map(occurrence -> new GuidebookEventChangeCandidate(
                        occurrence.id(),
                        occurrence.sourceContentId(),
                        occurrence.lifecycleChange() == ContentLifecycleChange.INACTIVATED))
                .toList();
    }

    private boolean affectsGuidebookSchedule(TourApiContentChangeOccurrence occurrence) {
        if (occurrence.lifecycleChange() == ContentLifecycleChange.NEW) {
            return false;
        }
        return occurrence.lifecycleChange() == ContentLifecycleChange.INACTIVATED
                || occurrence.changedFields().contains(ContentChangedField.START_DATE)
                || occurrence.changedFields().contains(ContentChangedField.END_DATE);
    }
}
