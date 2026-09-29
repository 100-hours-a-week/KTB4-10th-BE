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
    void returnsOnlySupportedTopLevelClassifications() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        MapContentQuery contentQuery = new MapContentQuery(jdbcTemplate);

        contentQuery.findWithinBounds(7L, 37.39, 127.10, 37.40, 127.12, 201);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(RowMapper.class), argumentsCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("content.classification_code_1 IN (?, ?, ?, ?, ?, ?)");
        assertThat(argumentsCaptor.getValue())
                .containsExactly(
                        7L,
                        "NA", "HS", "VE", "EX", "LS", "EV",
                        127.10, 37.39,
                        127.12, 37.39,
                        127.12, 37.40,
                        127.10, 37.40,
                        127.10, 37.39,
                        201);
    }
}
