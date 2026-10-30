package com.ktb10.kgb.content.tourapi;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 후속 알림과 재처리가 참조하는 영속 TourAPI 콘텐츠 변경 이력입니다. */
public record TourApiContentChangeOccurrence(
        Long id,
        UUID runId,
        String sourceContentId,
        Set<ContentChangedField> changedFields,
        ContentLifecycleChange lifecycleChange,
        TourApiContentLocation previousLocation,
        TourApiContentLocation currentLocation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public TourApiContentChangeOccurrence {
        Objects.requireNonNull(id, "변경 이력 ID는 필수입니다.");
        Objects.requireNonNull(runId, "동기화 실행 ID는 필수입니다.");
        Objects.requireNonNull(sourceContentId, "원본 콘텐츠 ID는 필수입니다.");
        changedFields = Set.copyOf(Objects.requireNonNull(
                changedFields,
                "변경 필드 목록은 필수입니다."));
        Objects.requireNonNull(lifecycleChange, "생명주기 변경값은 필수입니다.");
        Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
        Objects.requireNonNull(updatedAt, "수정 시각은 필수입니다.");
    }

    public ContentChangeSet toChangeSet() {
        return ContentChangeSet.of(
                sourceContentId,
                changedFields,
                lifecycleChange,
                previousLocation,
                currentLocation);
    }
}
