package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.content.service.MapContentCacheRefreshService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TourApiBaseImportTransactionMysqlIntegrationTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 10, 18);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private TourApiBaseImportTransactionService transactionService;

    @Autowired
    private TourApiCacheRefreshRepository refreshRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TourApiImportService importer;

    @MockitoBean
    private MapContentCacheRefreshService cacheRefreshService;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS tour_api_import_transaction_probe "
                        + "(id BIGINT NOT NULL PRIMARY KEY)");
        jdbcTemplate.update("DELETE FROM tour_api_import_transaction_probe");
        jdbcTemplate.update("DELETE FROM tour_api_cache_refresh_tiles");
        jdbcTemplate.update("DELETE FROM tour_api_cache_refresh_batches");
        when(cacheRefreshService.resolveTiles(any(), eq(SYNC_DATE))).thenReturn(List.of());
    }

    @Test
    void rollsBackContentChangesWhenRefreshPlanCannotBeSaved() {
        UUID runId = UUID.randomUUID();
        refreshRepository.save(runId, List.of(), Instant.parse("2026-10-18T00:00:00Z"));
        when(importer.importSnapshots(any(), any(), any())).thenAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO tour_api_import_transaction_probe (id) VALUES (1)");
            return new ImportSummary(1, 1, 0, 0, 0, 0, 0, 0);
        });

        assertThatThrownBy(() -> transactionService.importSnapshots(
                runId,
                SYNC_DATE,
                new byte[] {1},
                new byte[] {2},
                Instant.parse("2026-10-18T00:00:01Z")))
                .isInstanceOf(DuplicateKeyException.class);

        Integer importedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tour_api_import_transaction_probe",
                Integer.class);
        assertThat(importedRows).isZero();
        Integer refreshBatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tour_api_cache_refresh_batches",
                Integer.class);
        assertThat(refreshBatches).isOne();
    }
}
