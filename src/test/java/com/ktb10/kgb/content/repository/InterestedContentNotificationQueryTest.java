package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class InterestedContentNotificationQueryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate =
            mock(NamedParameterJdbcTemplate.class);
    private final InterestedContentNotificationQuery query =
            new InterestedContentNotificationQuery(jdbcTemplate);

    @Test
    void queriesActiveMembersWhoFavoritedChangedEvents() {
        when(jdbcTemplate.query(
                anyString(),
                anyMap(),
                ArgumentMatchers.<RowMapper<InterestedContentNotificationTarget>>any()))
                .thenReturn(List.of());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        query.findTargets(Set.of("100", "200"));

        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                eq(java.util.Map.of("sourceContentIds", Set.of("100", "200"))),
                ArgumentMatchers.<RowMapper<InterestedContentNotificationTarget>>any());
        assertThat(sqlCaptor.getValue())
                .contains("FROM favorite_contents")
                .contains("content.classification_code_1 = 'EV'")
                .contains("member.status = 'ACTIVE'")
                .contains("member.deleted_at IS NULL");
    }

    @Test
    void doesNotQueryWhenThereAreNoChangedContents() {
        assertThat(query.findTargets(Set.of())).isEmpty();

        verify(jdbcTemplate, never()).query(
                anyString(),
                anyMap(),
                ArgumentMatchers.<RowMapper<InterestedContentNotificationTarget>>any());
    }
}
