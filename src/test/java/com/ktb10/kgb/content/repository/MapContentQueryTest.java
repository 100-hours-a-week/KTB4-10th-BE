package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MapContentQueryTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loadsAllCacheableContentsWithoutSpatialBounds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        MapContentQuery contentQuery = new MapContentQuery(jdbcTemplate);

        contentQuery.findAllCacheable();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(RowMapper.class), argumentsCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains(
                        "content.status = 'ACTIVE'",
                        "content.deleted_at IS NULL",
                        "content.classification_code_1 IN (?, ?, ?, ?, ?, ?)")
                .doesNotContain("MBRContains");
        assertThat(argumentsCaptor.getValue())
                .containsExactly("NA", "HS", "VE", "EX", "LS", "EV");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void returnsOnlySupportedTopLevelClassifications() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        MapContentQuery contentQuery = new MapContentQuery(jdbcTemplate);

        contentQuery.findWithinBounds(37.39, 127.10, 37.40, 127.12);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(RowMapper.class), argumentsCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains(
                        "content.classification_code_1 IN (?, ?, ?, ?, ?, ?)",
                        "content.classification_code_1 <> 'EV'",
                        "event.end_date >= CURRENT_DATE")
                .doesNotContain(
                        "favorite_contents",
                        "member_guidebooks",
                        "AS is_in_guidebook",
                        "LIMIT ?");
        assertThat(argumentsCaptor.getValue())
                .containsExactly(
                        "NA", "HS", "VE", "EX", "LS", "EV",
                        127.10, 37.39,
                        127.12, 37.39,
                        127.12, 37.40,
                        127.10, 37.40,
                        127.10, 37.39);
    }
}
