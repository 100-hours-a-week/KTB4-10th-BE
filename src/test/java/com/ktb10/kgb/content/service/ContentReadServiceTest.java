package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.content.model.ContentSearchCategory;
import com.ktb10.kgb.content.repository.ContentReadRepository;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentDetailProjection;
import com.ktb10.kgb.content.repository.ContentSearchQuery;
import com.ktb10.kgb.content.repository.ContentSearchQuery.ContentSearchItem;
import com.ktb10.kgb.content.repository.ContentSearchQuery.ContentSearchPage;
import com.ktb10.kgb.content.repository.FavoriteContentQuery;
import com.ktb10.kgb.content.repository.MapContentPersonalization;
import com.ktb10.kgb.content.repository.MapContentPersonalizationQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentReadServiceTest {

    @Mock
    private ContentReadRepository contentReadRepository;

    @Mock
    private FavoriteContentQuery favoriteContentQuery;

    @Mock
    private ContentSearchQuery contentSearchQuery;

    @Mock
    private MapContentPersonalizationQuery personalizationQuery;

    @Mock
    private ContentDetailProjection detailProjection;

    @Test
    void returnsSearchPageWithTrimmedQueryAndPersonalization() {
        ContentSearchItem item = new ContentSearchItem(
                7L, "event-1", "서울 야행", "EVENT", "11", "서울특별시",
                "서울시", 37.5, 127.0, null,
                LocalDate.of(2099, 10, 1), LocalDate.of(2099, 10, 31));
        when(contentReadRepository.countProvinceByCode("11")).thenReturn(1L);
        when(contentSearchQuery.findContents(
                        ContentSearchCategory.ALL,
                        "야행",
                        "11",
                        LocalDate.of(2099, 10, 1),
                        LocalDate.of(2099, 10, 31),
                        2,
                        20))
                .thenReturn(new ContentSearchPage(List.of(item), 21));
        when(personalizationQuery.findByMemberAndContentIds(10L, List.of(7L)))
                .thenReturn(new MapContentPersonalization(Set.of(7L), Set.of(7L)));

        var response = service().getContents(
                10L, ContentSearchCategory.ALL,
                "  야행  ", " 11 ", "2099-10", 2, 20);

        assertThat(response.totalItems()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items().getFirst().contentType()).isEqualTo("EVENT");
        assertThat(response.items().getFirst().favorite()).isTrue();
        assertThat(response.items().getFirst().inGuidebook()).isTrue();
        verify(contentSearchQuery).findContents(
                ContentSearchCategory.ALL,
                "야행",
                "11",
                LocalDate.of(2099, 10, 1),
                LocalDate.of(2099, 10, 31),
                2,
                20);
    }

    @Test
    void keepsEmptyQueryOnlyForExistingEventList() {
        when(contentSearchQuery.findContents(
                        ContentSearchCategory.EVENT, null, null, null, null, 1, 20))
                .thenReturn(new ContentSearchPage(List.of(), 0));
        when(personalizationQuery.findByMemberAndContentIds(10L, List.of()))
                .thenReturn(MapContentPersonalization.empty());

        var response = service().getContents(
                10L, ContentSearchCategory.EVENT, "   ", null, null, 1, 20);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void rejectsMissingQueryForMapSearchCategories() {
        assertThatThrownBy(() -> service().getContents(
                10L, ContentSearchCategory.ALL, " ", null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().getContents(
                10L, ContentSearchCategory.PLACE, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesQueryAfterTrimming() {
        when(contentSearchQuery.findContents(
                        ContentSearchCategory.ALL,
                        "1234567890",
                        null,
                        null,
                        null,
                        1,
                        20))
                .thenReturn(new ContentSearchPage(List.of(), 0));
        when(personalizationQuery.findByMemberAndContentIds(10L, List.of()))
                .thenReturn(MapContentPersonalization.empty());

        service().getContents(
                10L, ContentSearchCategory.ALL, " 1234567890 ", null, null, 1, 20);

        assertThatThrownBy(() -> service().getContents(
                10L, ContentSearchCategory.ALL, "12345678901", null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUnknownProvinceCode() {
        when(contentReadRepository.countProvinceByCode("unknown")).thenReturn(0L);

        assertThatThrownBy(() -> service().getContents(
                10L, ContentSearchCategory.EVENT,
                null, "unknown", null, 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> service().getContents(
                10L, ContentSearchCategory.EVENT,
                null, null, "2099-13", 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void combinesEventDetailAndImages() {
        when(detailProjection.getId()).thenReturn(1L);
        when(detailProjection.getTitle()).thenReturn("수원 야행");
        when(detailProjection.getAddress()).thenReturn("수원시");
        when(contentReadRepository.findEventDetail("event-1"))
                .thenReturn(Optional.of(detailProjection));
        when(contentReadRepository.findImages(1L)).thenReturn(List.of());
        when(favoriteContentQuery.exists(10L, 1L)).thenReturn(true);

        var response = service().getEventDetail(10L, "event-1");

        assertThat(response.title()).isEqualTo("수원 야행");
        assertThat(response.eventDetail().eventPlace()).isEqualTo("수원시");
        assertThat(response.images()).isEmpty();
        assertThat(response.favorite()).isTrue();
    }

    @Test
    void rejectsMissingOrInactiveEvent() {
        when(contentReadRepository.findEventDetail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getEventDetail(10L, "missing"))
                .isInstanceOf(BusinessException.class);
    }

    private ContentReadService service() {
        return new ContentReadService(
                contentReadRepository,
                favoriteContentQuery,
                contentSearchQuery,
                personalizationQuery);
    }
}
