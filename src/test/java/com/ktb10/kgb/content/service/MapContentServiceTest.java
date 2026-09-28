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
    void returnsOnlyLimitAndMarksMoreResults() {
        MapContentItemResponse first = content("1");
        MapContentItemResponse second = content("2");
        MapContentItemResponse third = content("3");
        when(mapContentQuery.findWithinBounds(
                37.35, 127.05, 37.45, 127.15, 3))
                .thenReturn(List.of(first, second, third));

        MapContentResponse response = mapContentService.getContents(
                37.35, 127.05, 37.45, 127.15, 2);

        assertThat(response.items()).containsExactly(first, second);
        assertThat(response.hasMore()).isTrue();
        verify(mapContentQuery).findWithinBounds(
                37.35, 127.05, 37.45, 127.15, 3);
    }

    @Test
    void rejectsReversedBounds() {
        assertValidationFailure(() -> mapContentService.getContents(
                37.45, 127.05, 37.35, 127.15, 100));
        assertValidationFailure(() -> mapContentService.getContents(
                37.35, 127.15, 37.45, 127.05, 100));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteCoordinates() {
        assertValidationFailure(() -> mapContentService.getContents(
                -91.0, 127.05, 37.45, 127.15, 100));
        assertValidationFailure(() -> mapContentService.getContents(
                37.35, Double.NaN, 37.45, 127.15, 100));
    }

    @Test
    void rejectsBoundsWithDiagonalOverTwentyKilometers() {
        assertValidationFailure(() -> mapContentService.getContents(
                37.0, 127.0, 37.3, 127.3, 100));
    }

    @Test
    void acceptsBoundsWhoseDiagonalIsWithinTwentyKilometers() {
        when(mapContentQuery.findWithinBounds(
                37.35, 127.05, 37.45, 127.15, 101))
                .thenReturn(List.of());

        MapContentResponse response = mapContentService.getContents(
                37.35, 127.05, 37.45, 127.15, 100);

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
                null);
    }
}

