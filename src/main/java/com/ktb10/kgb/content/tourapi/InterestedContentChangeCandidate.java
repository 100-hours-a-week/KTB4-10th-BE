package com.ktb10.kgb.content.tourapi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** 관심 콘텐츠를 등록한 회원에게 알림할 수 있는 변경 후보입니다. */
public record InterestedContentChangeCandidate(
        Long occurrenceId,
        String sourceContentId,
        Set<ContentChangedField> changedFields) {

    public InterestedContentChangeCandidate {
        Objects.requireNonNull(occurrenceId, "변경 이력 ID는 필수입니다.");
        Objects.requireNonNull(sourceContentId, "원본 콘텐츠 ID는 필수입니다.");
        Objects.requireNonNull(changedFields, "변경 필드는 필수입니다.");
        if (changedFields.isEmpty()) {
            throw new IllegalArgumentException("알림 후보에는 변경 필드가 하나 이상 필요합니다.");
        }
        changedFields = Collections.unmodifiableSet(EnumSet.copyOf(changedFields));
    }
}
