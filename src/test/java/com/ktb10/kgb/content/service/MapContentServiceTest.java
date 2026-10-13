package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.observability.MapPerformanceMetrics;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentListResponse;
import com.ktb10.kgb.content.dto.MapMarkerItemResponse;
import com.ktb10.kgb.content.dto.MapMarkerResponse;
import com.ktb10.kgb.content.dto.MapMarkerResponse.Mode;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import com.ktb10.kgb.content.repository.MapContentPersonalization;
import com.ktb10.kgb.content.repository.MapContentPersonalizationQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapContentServiceTest {

    @Mock
    private MapContentPersonalizationQuery personalizationQuery;
    @Mock
    private MapContentCommonCache commonCache;

    private MapContentService mapContentService;

    @BeforeEach
    void setUp() {
        mapContentService = new MapContentService(
                new MapContentRadiusClusterer(),
                personalizationQuery,
                commonCache,
                new MapPerformanceMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void returnsAllContentsWithinRequestedBounds() {
        MapContentCommonData first = commonContent(1L);
        MapContentCommonData second = commonContent(2L);
        MapContentCommonData third = commonContent(3L);
        when(commonCache.get(
                37.35, 127.05, 37.45, 127.15))
                .thenReturn(List.of(first, second, third));
        when(personalizationQuery.findByMemberAndContentIds(1L, List.of(1L, 2L, 3L)))
                .thenReturn(new MapContentPersonalization(Set.of(1L), Set.of(2L)));

        MapMarkerResponse response = mapContentService.getMarkers(
                1L, 37.35, 127.05, 37.45, 127.15, 16);

        assertThat(response.mode()).isEqualTo(Mode.MARKER);
        assertThat(response.clusters()).isEmpty();
        assertThat(response.markers()).extracting(MapMarkerItemResponse::contentId)
                .containsExactly("1", "2", "3");
        assertThat(response.markers().get(0).favorite()).isTrue();
        assertThat(response.markers().get(1).inGuidebook()).isTrue();
        assertThat(response.markers().get(2).favorite()).isFalse();
        verify(commonCache).get(
                37.35, 127.05, 37.45, 127.15);
    }

    @Test
    void rejectsReversedBounds() {
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, 37.45, 127.05, 37.35, 127.15, 16));
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, 37.35, 127.15, 37.45, 127.05, 16));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteCoordinates() {
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, -91.0, 127.05, 37.45, 127.15, 16));
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, 37.35, Double.NaN, 37.45, 127.15, 16));
    }

    @Test
    void rejectsZoomOutsideSupportedRange() {
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, 37.35, 127.05, 37.45, 127.15, 5));
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, 37.35, 127.05, 37.45, 127.15, 22));
    }

    @Test
    void acceptsLargeValidBoundsFromClient() {
        when(commonCache.get(33.0, 124.0, 39.0, 132.0)).thenReturn(List.of());
        MapMarkerResponse response = mapContentService.getMarkers(
                1L, 33.0, 124.0, 39.0, 132.0, 16);

        assertThat(response.markers()).isEmpty();
        assertThat(response.mode()).isEqualTo(Mode.CLUSTER);
    }

    @Test
    void rejectsBoundsExceedingMaximumTileCount() {
        assertValidationFailure(() -> mapContentService.getMarkers(
                1L, -90.0, -180.0, 90.0, 180.0, 6));
        assertValidationFailure(() -> mapContentService.getBottomSheetContents(
                1L, -90.0, -180.0, 90.0, 180.0, null));
    }

    @Test
    void returnsAllClusterRepresentativesForWideZoom() {
        List<MapContentCommonData> contents = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> commonContent(
                        (long) index,
                        33.0 + index * 0.2,
                        124.0 + index * 0.2))
                .toList();
        when(commonCache.get(33.0, 124.0, 39.0, 132.0)).thenReturn(contents);
        MapMarkerResponse response = mapContentService.getMarkers(
                1L, 33.0, 124.0, 39.0, 132.0, 14);

        assertThat(response.mode()).isEqualTo(Mode.CLUSTER);
        assertThat(response.clusters()).hasSize(21);
        assertThat(response.markers()).isEmpty();
    }

    @Test
    void returnsBottomSheetContentsTenAtATimeAndPersonalizesOnlyCurrentPage() {
        List<MapContentCommonData> contents = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> commonContent((long) index, 37.4, 127.1 + index * 0.001))
                .toList();
        when(commonCache.get(37.0, 127.0, 38.0, 128.0)).thenReturn(contents);
        when(personalizationQuery.findByMemberAndContentIds(eq(1L), anyList()))
                .thenReturn(MapContentPersonalization.empty());

        MapContentListResponse first = mapContentService.getBottomSheetContents(
                1L, 37.0, 127.0, 38.0, 128.0, null);
        MapContentListResponse second = mapContentService.getBottomSheetContents(
                1L, 37.0, 127.0, 38.0, 128.0, first.nextCursor());

        assertThat(first.items()).hasSize(10);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(second.items()).hasSize(2);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextCursor()).isNull();
        verify(personalizationQuery).findByMemberAndContentIds(
                1L, first.items().stream().map(item -> Long.valueOf(item.contentId())).toList());
    }

    @Test
    void rejectsBottomSheetCursorCreatedForDifferentBounds() {
        when(commonCache.get(37.0, 127.0, 38.0, 128.0)).thenReturn(
                java.util.stream.IntStream.rangeClosed(1, 12)
                        .mapToObj(index -> commonContent((long) index))
                        .toList());
        when(personalizationQuery.findByMemberAndContentIds(eq(1L), anyList()))
                .thenReturn(MapContentPersonalization.empty());
        String cursor = mapContentService.getBottomSheetContents(
                1L, 37.0, 127.0, 38.0, 128.0, null).nextCursor();

        assertValidationFailure(() -> mapContentService.getBottomSheetContents(
                1L, 37.1, 127.0, 38.0, 128.0, cursor));
    }

    @Test
    void capsClusterZoomForNationwideBounds() {
        List<MapContentCommonData> contents = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> commonContent(
                        (long) index,
                        37.5,
                        127.0 + index * 0.001))
                .toList();
        when(commonCache.get(33.0, 124.0, 39.0, 132.0)).thenReturn(contents);
        MapMarkerResponse response = mapContentService.getMarkers(
                1L, 33.0, 124.0, 39.0, 132.0, 21);

        assertThat(response.mode()).isEqualTo(Mode.CLUSTER);
        assertThat(response.clusters()).hasSize(1);
        assertThat(response.clusters().getFirst().count()).isEqualTo(12);
    }

    private void assertValidationFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.COMMON_VALIDATION_ERROR));
    }

    private MapContentCommonData commonContent(Long contentId) {
        return commonContent(contentId, 37.4, 127.1);
    }

    private MapContentCommonData commonContent(Long contentId, double latitude, double longitude) {
        return new MapContentCommonData(
                contentId,
                String.valueOf(contentId),
                "장소 " + contentId,
                ContentType.PLACE,
                "주소",
                latitude,
                longitude,
                null,
                null);
    }
}
