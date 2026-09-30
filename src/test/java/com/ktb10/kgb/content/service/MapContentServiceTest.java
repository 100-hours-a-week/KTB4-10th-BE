package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapClusterResponse;
import com.ktb10.kgb.content.dto.MapContentResponse;
import com.ktb10.kgb.content.dto.MapContentResponse.Mode;
import com.ktb10.kgb.content.repository.MapClusterQueryResult;
import com.ktb10.kgb.content.repository.MapContentQuery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapContentServiceTest {

    @Mock
    private MapContentQuery mapContentQuery;

    private MapContentService mapContentService;

    @BeforeEach
    void setUp() {
        mapContentService = new MapContentService(mapContentQuery);
    }

    @Test
    void returnsAllContentsWithinRequestedBounds() {
        MapContentItemResponse first = content("1");
        MapContentItemResponse second = content("2");
        MapContentItemResponse third = content("3");
        when(mapContentQuery.findWithinBounds(
                1L, 37.35, 127.05, 37.45, 127.15))
                .thenReturn(List.of(first, second, third));

        MapContentResponse response = mapContentService.getContents(
                1L, 37.35, 127.05, 37.45, 127.15, 16);

        assertThat(response.mode()).isEqualTo(Mode.CONTENT);
        assertThat(response.clusters()).isEmpty();
        assertThat(response.items()).containsExactly(first, second, third);
        assertThat(response.hasMore()).isFalse();
        verify(mapContentQuery).findWithinBounds(
                1L, 37.35, 127.05, 37.45, 127.15);
    }

    @Test
    void rejectsReversedBounds() {
        assertValidationFailure(() -> mapContentService.getContents(
                1L, 37.45, 127.05, 37.35, 127.15, 16));
        assertValidationFailure(() -> mapContentService.getContents(
                1L, 37.35, 127.15, 37.45, 127.05, 16));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteCoordinates() {
        assertValidationFailure(() -> mapContentService.getContents(
                1L, -91.0, 127.05, 37.45, 127.15, 16));
        assertValidationFailure(() -> mapContentService.getContents(
                1L, 37.35, Double.NaN, 37.45, 127.15, 16));
    }

    @Test
    void acceptsLargeValidBoundsFromClient() {
        when(mapContentQuery.findClustersWithinBounds(
                1L, 33.0, 124.0, 39.0, 132.0, 1.0))
                .thenReturn(List.of());

        MapContentResponse response = mapContentService.getContents(
                1L, 33.0, 124.0, 39.0, 132.0, 16);

        assertThat(response.items()).isEmpty();
        assertThat(response.mode()).isEqualTo(Mode.CLUSTER);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void returnsClustersAndAtMostTwentyRepresentativesForWideZoom() {
        List<MapClusterQueryResult> results = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> new MapClusterQueryResult(
                        new MapClusterResponse(
                                "cluster-" + index,
                                37.0 + index / 100.0,
                                127.0,
                                100 - index),
                        content(String.valueOf(index))))
                .toList();
        when(mapContentQuery.findClustersWithinBounds(
                1L, 33.0, 124.0, 39.0, 132.0, 1.0))
                .thenReturn(results);

        MapContentResponse response = mapContentService.getContents(
                1L, 33.0, 124.0, 39.0, 132.0, 14);

        assertThat(response.mode()).isEqualTo(Mode.CLUSTER);
        assertThat(response.clusters()).hasSize(21);
        assertThat(response.items()).hasSize(20);
        assertThat(response.items().getFirst().contentId()).isEqualTo("1");
        assertThat(response.hasMore()).isFalse();
        verify(mapContentQuery).findClustersWithinBounds(
                1L, 33.0, 124.0, 39.0, 132.0, 1.0);
    }

    private void assertValidationFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.COMMON_VALIDATION_ERROR));
    }

    private MapContentItemResponse content(String contentId) {
        return new MapContentItemResponse(
                contentId,
                "장소 " + contentId,
                ContentType.PLACE,
                "주소",
                37.4,
                127.1,
                null,
                null,
                false,
                false);
    }
}
