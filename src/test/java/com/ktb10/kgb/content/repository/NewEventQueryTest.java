package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NewEventQueryTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void filtersActiveEventsByHalfOpenSourceCreatedAtRange() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        NewEventQuery query = new NewEventQuery(jdbcTemplate);
        LocalDateTime start = LocalDateTime.of(2026, 10, 14, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 15, 0, 0);

        query.findBySourceCreatedAt(start, end, 20, 40L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(RowMapper.class), argumentsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains(
                "JOIN event_details",
                "content.status = 'ACTIVE'",
                "content.deleted_at IS NULL",
                "content.classification_code_1 = 'EV'",
                "content.source_created_at >= ?",
                "content.source_created_at < ?",
                "ORDER BY content.source_created_at DESC, content.id DESC",
                "LIMIT ? OFFSET ?");
        assertThat(argumentsCaptor.getValue()).containsExactly(
                Timestamp.valueOf(start),
                Timestamp.valueOf(end),
                20,
                40L);
    }
}
