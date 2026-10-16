package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.content.repository.ContentReadRepository;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentDetailProjection;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentListProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ContentReadServiceTest {

    @Mock
    private ContentReadRepository contentReadRepository;

    @Mock
    private ContentListProjection listProjection;

    @Mock
    private ContentDetailProjection detailProjection;

    @Test
    void returnsEventPageWithTrimmedSearchQuery() {
        ContentReadService service = new ContentReadService(contentReadRepository);
        var pageable = PageRequest.of(1, 20);
        when(listProjection.getContentId()).thenReturn("event-1");
        when(listProjection.getStartDate()).thenReturn(LocalDate.of(2026, 8, 14));
        when(contentReadRepository.findEvents("야행", pageable))
                .thenReturn(new PageImpl<>(List.of(listProjection), pageable, 21));

        var response = service.getEvents("  야행  ", 2, 20);

        assertThat(response.totalItems()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items().getFirst().eventPeriod().startDate())
                .isEqualTo(LocalDate.of(2026, 8, 14));
        verify(contentReadRepository).findEvents("야행", pageable);
    }

    @Test
    void combinesEventDetailAndImages() {
        ContentReadService service = new ContentReadService(contentReadRepository);
        when(detailProjection.getId()).thenReturn(1L);
        when(detailProjection.getTitle()).thenReturn("수원 야행");
        when(detailProjection.getAddress()).thenReturn("수원시");
        when(contentReadRepository.findEventDetail("event-1"))
                .thenReturn(Optional.of(detailProjection));
        when(contentReadRepository.findImages(1L)).thenReturn(List.of());

        var response = service.getEventDetail("event-1");

        assertThat(response.title()).isEqualTo("수원 야행");
        assertThat(response.eventDetail().eventPlace()).isEqualTo("수원시");
        assertThat(response.images()).isEmpty();
    }

    @Test
    void rejectsMissingOrInactiveEvent() {
        ContentReadService service = new ContentReadService(contentReadRepository);
        when(contentReadRepository.findEventDetail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventDetail("missing"))
                .isInstanceOf(BusinessException.class);
    }
}
