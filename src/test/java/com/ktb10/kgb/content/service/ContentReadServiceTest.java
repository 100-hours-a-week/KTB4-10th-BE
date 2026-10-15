package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.content.repository.ContentDetailData;
import com.ktb10.kgb.content.repository.ContentListData;
import com.ktb10.kgb.content.repository.ContentReadQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentReadServiceTest {

    @Mock
    private ContentReadQuery contentReadQuery;

    @Test
    void returnsEventPageWithTrimmedSearchQuery() {
        ContentReadService service = new ContentReadService(contentReadQuery);
        when(contentReadQuery.countEvents("야행")).thenReturn(21L);
        when(contentReadQuery.findEvents("야행", 20, 20L)).thenReturn(List.of(
                new ContentListData(
                        "event-1", "수원 야행", "41", "경기도", "수원시", null,
                        LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 30))));

        var response = service.getEvents("  야행  ", 2, 20);

        assertThat(response.totalItems()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items().getFirst().eventPeriod().startDate())
                .isEqualTo(LocalDate.of(2026, 8, 14));
        verify(contentReadQuery).findEvents("야행", 20, 20L);
    }

    @Test
    void combinesEventDetailAndImages() {
        ContentReadService service = new ContentReadService(contentReadQuery);
        ContentDetailData detail = new ContentDetailData(
                1L, "event-1", "수원 야행", "41", "경기도",
                37.2, 127.0, null, "소개", "수원시", "031", null,
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 30),
                "18:00~23:00", "수원시");
        when(contentReadQuery.findEventDetail("event-1")).thenReturn(Optional.of(detail));
        when(contentReadQuery.findImages(1L)).thenReturn(List.of());

        var response = service.getEventDetail("event-1");

        assertThat(response.title()).isEqualTo("수원 야행");
        assertThat(response.eventDetail().eventPlace()).isEqualTo("수원시");
        assertThat(response.images()).isEmpty();
    }

    @Test
    void rejectsMissingOrInactiveEvent() {
        ContentReadService service = new ContentReadService(contentReadQuery);
        when(contentReadQuery.findEventDetail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventDetail("missing"))
                .isInstanceOf(BusinessException.class);
    }
}
