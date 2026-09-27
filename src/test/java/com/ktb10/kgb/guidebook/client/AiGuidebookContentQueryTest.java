package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Content;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AiGuidebookContentQueryTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void limitsCandidatesByRegion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        AiGuidebookContentQuery contentQuery =
                new AiGuidebookContentQuery(jdbcTemplate);

        List<Content> result = contentQuery.findAll(
                "충청북도",
                "청주시",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(RowMapper.class), argumentsCaptor.capture());
        assertThat(result).isEmpty();
        assertThat(sqlCaptor.getValue()).contains(
                "event.start_date <= ?",
                "event.end_date >= ?",
                "ORDER BY content.id",
                "LIMIT ?");
        assertThat(argumentsCaptor.getValue())
                .containsExactly(
                        "충청북도",
                        "청주시",
                        LocalDate.of(2026, 10, 14),
                        LocalDate.of(2026, 10, 12),
                        100);
    }
}
