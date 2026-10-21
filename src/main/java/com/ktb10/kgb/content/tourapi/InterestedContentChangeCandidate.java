package com.ktb10.kgb.content.tourapi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** 관심 콘텐츠를 등록한 회원에게 알림할 수 있는 변경 후보입니다. */
public record InterestedContentChangeCandidate(
        String sourceContentId,
        Set<ContentChangedField> changedFields) {

    public InterestedContentChangeCandidate {
        Objects.requireNonNull(sourceContentId);
        Objects.requireNonNull(changedFields);
        if (changedFields.isEmpty()) {
            throw new IllegalArgumentException("알림 후보에는 변경 필드가 하나 이상 필요합니다.");
        }
        changedFields = Collections.unmodifiableSet(EnumSet.copyOf(changedFields));
    }
}
