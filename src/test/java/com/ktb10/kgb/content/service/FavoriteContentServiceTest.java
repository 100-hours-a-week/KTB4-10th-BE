package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.repository.FavoriteContentQuery;
import com.ktb10.kgb.content.repository.FavoriteContentQuery.FavoriteContentListRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteContentServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-28T04:00:00Z"), ZoneOffset.UTC);

    @Mock
    private FavoriteContentQuery favoriteContentQuery;

    private FavoriteContentService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteContentService(favoriteContentQuery, CLOCK);
    }

    @Test
    void savesActiveContentIdempotently() {
        when(favoriteContentQuery.findActiveContentId("126508"))
                .thenReturn(Optional.of(20L));

        var response = service.save(1L, "126508");

        assertThat(response.contentId()).isEqualTo("126508");
        assertThat(response.favorite()).isTrue();
        verify(favoriteContentQuery).save(
                1L, 20L, LocalDateTime.of(2026, 9, 28, 4, 0));
    }

    @Test
    void rejectsInactiveOrMissingContent() {
        when(favoriteContentQuery.findActiveContentId("missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(1L, "missing"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void deletesEvenWhenFavoriteDoesNotExist() {
        service.delete(1L, "126508");

        verify(favoriteContentQuery).delete(1L, "126508");
    }

    @Test
    void returnsFavoritesWithCompositeCursor() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 28, 4, 0);
        FavoriteContentListRow newest = favorite(3L, createdAt, "event-1", ContentType.EVENT);
        FavoriteContentListRow middle = favorite(2L, createdAt, "place-2", ContentType.PLACE);
        FavoriteContentListRow oldest = favorite(1L, createdAt, "place-1", ContentType.PLACE);
        when(favoriteContentQuery.findPage(1L, 3))
                .thenReturn(List.of(newest, middle, oldest));

        var firstPage = service.getFavorites(1L, null, 2);

        assertThat(firstPage.items())
                .extracting(item -> item.contentId())
                .containsExactly("event-1", "place-2");
        assertThat(firstPage.items().getFirst().eventPeriod()).isNotNull();
        assertThat(firstPage.items().get(1).eventPeriod()).isNull();
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        when(favoriteContentQuery.findPageAfter(1L, createdAt, 2L, 3))
                .thenReturn(List.of(oldest));

        var secondPage = service.getFavorites(1L, firstPage.nextCursor(), 2);

        assertThat(secondPage.items())
                .extracting(item -> item.contentId())
                .containsExactly("place-1");
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(secondPage.hasMore()).isFalse();
    }

    @Test
    void rejectsMalformedOrUnknownCursor() {
        assertThatThrownBy(() -> service.getFavorites(1L, "invalid-cursor", 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.COMMON_VALIDATION_ERROR));
    }

    @Test
    void returnsEmptyFavoriteList() {
        when(favoriteContentQuery.findPage(1L, 21)).thenReturn(List.of());

        var response = service.getFavorites(1L, null, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void usesStructurallyValidCursorWithoutRequiringItsRowToStillExist() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 28, 4, 0);
        FavoriteContentListRow cursorRow = favorite(3L, createdAt, "event-1", ContentType.EVENT);
        when(favoriteContentQuery.findPage(1L, 2)).thenReturn(List.of(
                cursorRow,
                favorite(2L, createdAt.minusSeconds(1), "place-1", ContentType.PLACE)));
        String cursor = service.getFavorites(1L, null, 1).nextCursor();
        when(favoriteContentQuery.findPageAfter(2L, createdAt, 3L, 21))
                .thenReturn(List.of());

        var response = service.getFavorites(2L, cursor, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasMore()).isFalse();
    }

    private FavoriteContentListRow favorite(
            Long favoriteId,
            LocalDateTime createdAt,
            String contentId,
            ContentType contentType) {
        return new FavoriteContentListRow(
                favoriteId,
                createdAt,
                contentId,
                "관광 콘텐츠",
                contentType,
                "서울특별시",
                null,
                contentType == ContentType.EVENT
                        ? java.time.LocalDate.of(2026, 10, 24)
                        : null,
                contentType == ContentType.EVENT
                        ? java.time.LocalDate.of(2026, 10, 25)
                        : null);
    }
}
