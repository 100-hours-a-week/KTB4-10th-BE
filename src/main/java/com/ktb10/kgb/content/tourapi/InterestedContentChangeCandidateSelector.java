package com.ktb10.kgb.content.tourapi;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** TourAPI 변경 셋에서 사용자에게 의미 있는 관심 콘텐츠 변경만 선별합니다. */
@Component
public class InterestedContentChangeCandidateSelector {

    private static final Set<ContentChangedField> NOTIFIABLE_FIELDS = EnumSet.of(
            ContentChangedField.TITLE,
            ContentChangedField.DESCRIPTION,
            ContentChangedField.PHONE,
            ContentChangedField.HOMEPAGE_URL,
            ContentChangedField.ADDRESS,
            ContentChangedField.START_DATE,
            ContentChangedField.END_DATE,
            ContentChangedField.OPERATING_HOURS,
            ContentChangedField.ORGANIZER,
            ContentChangedField.ORGANIZER_PHONE,
            ContentChangedField.EVENT_PLACE,
            ContentChangedField.STATUS);

    public List<InterestedContentChangeCandidate> select(
            Collection<ContentChangeSet> changeSets) {
        return changeSets.stream()
                .filter(change -> change.lifecycleChange() != ContentLifecycleChange.NEW)
                .map(this::toCandidate)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private java.util.Optional<InterestedContentChangeCandidate> toCandidate(
            ContentChangeSet changeSet) {
        EnumSet<ContentChangedField> fields = EnumSet.noneOf(ContentChangedField.class);
        fields.addAll(changeSet.changedFields());
        fields.retainAll(NOTIFIABLE_FIELDS);
        if (fields.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new InterestedContentChangeCandidate(
                changeSet.sourceContentId(),
                fields));
    }
}
