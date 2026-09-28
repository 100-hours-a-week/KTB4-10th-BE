package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.FavoriteContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.repository.FavoriteContentQuery;
import com.ktb10.kgb.content.repository.FavoriteContentQuery.FavoriteContentRow;
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
    void returnsCursorPageInRepositoryOrder() {
        FavoriteContentRow first = row(3L, LocalDateTime.of(2026, 9, 28, 3, 0));
        FavoriteContentRow second = row(2L, LocalDateTime.of(2026, 9, 28, 2, 0));
        FavoriteContentRow third = row(1L, LocalDateTime.of(2026, 9, 28, 1, 0));
        when(favoriteContentQuery.findAll(1L, 3))
                .thenReturn(List.of(first, second, third));

        var firstPage = service.getFavorites(1L, null, 2);

        assertThat(firstPage.items())
                .extracting(FavoriteContentItemResponse::contentId)
                .containsExactly("content-3", "content-2");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        when(favoriteContentQuery.findAllAfter(
                1L, second.favoritedAt(), second.favoriteId(), 3))
                .thenReturn(List.of(third));

        var secondPage = service.getFavorites(1L, firstPage.nextCursor(), 2);

        assertThat(secondPage.items())
                .extracting(FavoriteContentItemResponse::contentId)
                .containsExactly("content-1");
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> service.getFavorites(1L, "invalid", 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonErrorCode.COMMON_VALIDATION_ERROR));
    }

    private FavoriteContentRow row(Long id, LocalDateTime createdAt) {
        FavoriteContentItemResponse item = new FavoriteContentItemResponse(
                "content-" + id,
                "장소 " + id,
                ContentType.PLACE,
                "주소",
                37.4,
                127.1,
                null,
                null,
                createdAt.atOffset(ZoneOffset.UTC));
        return new FavoriteContentRow(id, createdAt, item);
    }
}
