package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.model.ContentSearchCategory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class ContentSearchQueryTest {

    @Test
    void excludesContentsWithoutLocationFromItemsAndCount() {
        NamedParameterJdbcTemplate jdbcTemplate =
                mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ContentSearchQuery.ContentSearchItem>>any()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<Class<Long>>any()))
                .thenReturn(0L);
        ContentSearchQuery query = new ContentSearchQuery(jdbcTemplate);

        query.findContents(
                ContentSearchCategory.ALL,
                "공원",
                null,
                null,
                null,
                1,
                20);

        ArgumentCaptor<String> itemSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                itemSql.capture(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ContentSearchQuery.ContentSearchItem>>any());
        verify(jdbcTemplate).queryForObject(
                countSql.capture(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<Class<Long>>any());

        assertThat(itemSql.getValue()).contains("content.location IS NOT NULL");
        assertThat(countSql.getValue()).contains("content.location IS NOT NULL");
    }
}
