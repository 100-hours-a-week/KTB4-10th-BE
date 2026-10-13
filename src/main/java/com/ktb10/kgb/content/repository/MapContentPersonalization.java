package com.ktb10.kgb.content.repository;

import java.util.Set;

/** 지도 콘텐츠별 사용자 상태입니다. */
public record MapContentPersonalization(
        Set<Long> favoriteContentIds,
        Set<Long> guidebookContentIds) {

    public MapContentPersonalization {
        favoriteContentIds = Set.copyOf(favoriteContentIds);
        guidebookContentIds = Set.copyOf(guidebookContentIds);
    }

    public static MapContentPersonalization empty() {
        return new MapContentPersonalization(Set.of(), Set.of());
    }

    public boolean isFavorite(Long contentId) {
        return favoriteContentIds.contains(contentId);
    }

    public boolean isInGuidebook(Long contentId) {
        return guidebookContentIds.contains(contentId);
    }
}
