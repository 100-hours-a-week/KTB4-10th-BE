package com.ktb10.kgb.content.tourapi;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 한 콘텐츠에서 실제로 달라진 값을 담습니다. */
public record ContentChangeSet(
        String sourceContentId,
        Set<ContentChangedField> changedFields,
        ContentLifecycleChange lifecycleChange,
        TourApiContentLocation previousLocation,
        TourApiContentLocation currentLocation) {

    public ContentChangeSet {
        Objects.requireNonNull(sourceContentId);
        Objects.requireNonNull(changedFields);
        Objects.requireNonNull(lifecycleChange);
        changedFields = immutableCopy(changedFields);
    }

    public static ContentChangeSet of(
            String sourceContentId,
            Set<ContentChangedField> changedFields,
            ContentLifecycleChange lifecycleChange,
            TourApiContentLocation previousLocation,
            TourApiContentLocation currentLocation) {
        return new ContentChangeSet(
                sourceContentId,
                changedFields,
                lifecycleChange,
                previousLocation,
                currentLocation);
    }

    public ContentChangeSet merge(ContentChangeSet other) {
        if (!sourceContentId.equals(other.sourceContentId)) {
            throw new IllegalArgumentException("서로 다른 콘텐츠의 변경 셋은 합칠 수 없습니다.");
        }
        EnumSet<ContentChangedField> fields = EnumSet.noneOf(ContentChangedField.class);
        fields.addAll(changedFields);
        fields.addAll(other.changedFields);
        ContentLifecycleChange mergedLifecycle = lifecycleChange != ContentLifecycleChange.NONE
                ? lifecycleChange
                : other.lifecycleChange;
        return of(
                sourceContentId,
                fields,
                mergedLifecycle,
                previousLocation != null ? previousLocation : other.previousLocation,
                other.currentLocation != null ? other.currentLocation : currentLocation);
    }

    public static List<ContentChangeSet> mergeAll(
            Collection<ContentChangeSet> changes) {
        Map<String, ContentChangeSet> merged = new LinkedHashMap<>();
        changes.forEach(change -> merged.merge(
                change.sourceContentId,
                change,
                ContentChangeSet::merge));
        return List.copyOf(merged.values());
    }

    private static <E extends Enum<E>> Set<E> immutableCopy(Set<E> values) {
        if (values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}
