package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentResponse;
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
                1L, 37.35, 127.05, 37.45, 127.15);

        assertThat(response.items()).containsExactly(first, second, third);
        assertThat(response.hasMore()).isFalse();
        verify(mapContentQuery).findWithinBounds(
                1L, 37.35, 127.05, 37.45, 127.15);
    }

    @Test
    void rejectsReversedBounds() {
        assertValidationFailure(() -> mapContentService.getContents(
                1L, 37.45, 127.05, 37.35, 127.15));
        assertValidationFailure(() -> mapContentService.getContents(
                1L, 37.35, 127.15, 37.45, 127.05));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteCoordinates() {
        assertValidationFailure(() -> mapContentService.getContents(
                1L, -91.0, 127.05, 37.45, 127.15));
        assertValidationFailure(() -> mapContentService.getContents(
                1L, 37.35, Double.NaN, 37.45, 127.15));
    }

    @Test
    void acceptsLargeValidBoundsFromClient() {
        when(mapContentQuery.findWithinBounds(
                1L, 33.0, 124.0, 39.0, 132.0))
                .thenReturn(List.of());

        MapContentResponse response = mapContentService.getContents(
                1L, 33.0, 124.0, 39.0, 132.0);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasMore()).isFalse();
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
