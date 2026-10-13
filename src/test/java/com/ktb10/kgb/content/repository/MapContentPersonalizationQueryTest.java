package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class MapContentPersonalizationQueryTest {

    @Test
    void skipsQueryWhenContentIdsAreEmpty() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MapContentPersonalizationQuery query = new MapContentPersonalizationQuery(jdbcTemplate);

        MapContentPersonalization result = query.findByMemberAndContentIds(7L, List.of());

        assertThat(result).isEqualTo(MapContentPersonalization.empty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void queriesOnlyRequestedContentIdsForMember() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MapContentPersonalizationQuery query = new MapContentPersonalizationQuery(jdbcTemplate);

        query.findByMemberAndContentIds(7L, List.of(11L, 12L));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowCallbackHandler.class),
                argumentsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains(
                        "favorite.member_id = ?",
                        "favorite.content_id IN (?, ?)",
                        "member_guidebook.member_id = ?",
                        "member_guidebook.deleted_at IS NULL",
                        "item.tourism_content_id IN (?, ?)");
        assertThat(argumentsCaptor.getValue())
                .containsExactly(7L, 11L, 12L, 7L, 11L, 12L);
    }
}
