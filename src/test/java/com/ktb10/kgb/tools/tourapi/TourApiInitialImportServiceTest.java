package com.ktb10.kgb.tools.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TourApiInitialImportServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final List<Integer> batchSizes = new ArrayList<>();
    private TourApiInitialImportService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() throws Exception {
        service = new TourApiInitialImportService(new ObjectMapper(), jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenReturn(10L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("name")).thenReturn("청주시");
                    when(resultSet.getLong("id")).thenReturn(20L);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
        when(jdbcTemplate.batchUpdate(
                anyString(), any(BatchPreparedStatementSetter.class)))
                .thenAnswer(invocation -> {
                    BatchPreparedStatementSetter setter = invocation.getArgument(1);
                    batchSizes.add(setter.getBatchSize());
                    return new int[setter.getBatchSize()];
                });
    }

    @Test
    void rejectsMissingJsonFile() {
        Path missing = tempDir.resolve("missing.json");

        assertThatThrownBy(() -> service.importFiles(missing, missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON 파일을 찾을 수 없습니다");
    }

    @Test
    void rejectsResponseWithoutItemArray() throws Exception {
        Path areaFile = write("area.json", "{\"response\":{\"body\":{\"items\":{}}}}");

        assertThatThrownBy(() -> service.importFiles(areaFile, areaFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TourAPI item 배열을 찾을 수 없습니다");
    }

    @Test
    void importsContentAndMatchingEvent() throws Exception {
        Path areaFile = writeItems("area.json", """
                {
                  "contentid": "100",
                  "contenttypeid": "12",
                  "title": "청주 관광지",
                  "addr1": "충청북도 청주시 상당구",
                  "mapx": "127.5",
                  "mapy": "36.6",
                  "lDongRegnCd": "43",
                  "lDongSignguCd": "110",
                  "lclsSystm1": "NA",
                  "lclsSystm2": "NA01",
                  "lclsSystm3": "NA010100",
                  "createdtime": "20260101120000",
                  "modifiedtime": "20260102120000"
                }
                """);
        Path festivalFile = writeItems("festival.json", """
                {
                  "contentid": "100",
                  "eventstartdate": "20261012",
                  "eventenddate": "20261014"
                }
                """);

        ImportSummary summary = service.importFiles(areaFile, festivalFile);

        assertThat(summary.sourceContents()).isEqualTo(1);
        assertThat(summary.importedContents()).isEqualTo(1);
        assertThat(summary.importedEvents()).isEqualTo(1);
        assertThat(summary.skippedMissingCoordinates()).isZero();
        assertThat(summary.skippedMissingRegionMapping()).isZero();
        assertThat(summary.skippedInvalidEvents()).isZero();
        assertThat(batchSizes).containsExactly(1, 1);
    }

    @Test
    void countsSkippedContentsAndInvalidEvents() throws Exception {
        Path areaFile = writeItems("area.json", """
                {
                  "contentid": "101",
                  "title": "좌표 없는 콘텐츠",
                  "addr1": "충청북도 청주시",
                  "lDongRegnCd": "43",
                  "lDongSignguCd": "110",
                  "lclsSystm1": "NA"
                },
                {
                  "contentid": "102",
                  "title": "지역 없는 콘텐츠",
                  "addr1": "알 수 없는 지역",
                  "mapx": "127.5",
                  "mapy": "36.6",
                  "lDongRegnCd": "99",
                  "lDongSignguCd": "999",
                  "lclsSystm1": "NA"
                }
                """);
        Path festivalFile = writeItems("festival.json", """
                {
                  "contentid": "101",
                  "eventstartdate": "20261014",
                  "eventenddate": "20261012"
                }
                """);

        ImportSummary summary = service.importFiles(areaFile, festivalFile);

        assertThat(summary.sourceContents()).isEqualTo(2);
        assertThat(summary.importedContents()).isZero();
        assertThat(summary.skippedMissingCoordinates()).isEqualTo(1);
        assertThat(summary.skippedMissingRegionMapping()).isEqualTo(1);
        assertThat(summary.sourceEvents()).isEqualTo(1);
        assertThat(summary.importedEvents()).isZero();
        assertThat(summary.skippedInvalidEvents()).isEqualTo(1);
        assertThat(batchSizes).containsExactly(0, 0);
    }

    private Path writeItems(String fileName, String items) throws Exception {
        return write(fileName, """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": [%s]
                      }
                    }
                  }
                }
                """.formatted(items));
    }

    private Path write(String fileName, String content) throws Exception {
        return Files.writeString(tempDir.resolve(fileName), content);
    }
}
