package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MapContentQueryMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MapContentQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long regionId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO regions (administrative_code, name, region_level)
                VALUES ('11', '서울특별시', 'PROVINCE')
                """);
        regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '11'", Long.class);
    }

    @Test
    void filtersTourApiCategoriesUsingMysqlValues() {
        insertPlace("nature-park", "NA04", 126.98, 37.56);
        insertPlace("urban-park", "VE03", 126.99, 37.57);
        insertPlace("unused-culture", "VE05", 127.00, 37.58);
        insertPlace("unknown", "ZZ99", 127.01, 37.59);
        insertEvent("active-event", LocalDate.now().plusDays(1), 127.02, 37.60);
        insertEvent("expired-event", LocalDate.now().minusDays(1), 127.03, 37.61);

        assertThat(query.findAllCacheable())
                .extracting(MapContentCommonData::contentId)
                .containsExactly("nature-park", "urban-park", "active-event");

        assertThat(query.findWithinBounds(37.50, 126.90, 37.70, 127.10))
                .extracting(MapContentCommonData::contentId)
                .containsExactly("nature-park", "urban-park", "active-event");
    }

    private void insertPlace(String sourceContentId, String code, double longitude, double latitude) {
        insertContent(sourceContentId, "NA", code, longitude, latitude);
    }

    private void insertEvent(
            String sourceContentId, LocalDate endDate, double longitude, double latitude) {
        insertContent(sourceContentId, "EV", null, longitude, latitude);
        Long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
        jdbcTemplate.update("""
                INSERT INTO event_details (content_id, start_date, end_date, updated_at)
                VALUES (?, ?, ?, ?)
                """, contentId, LocalDate.now().minusDays(1), endDate, LocalDateTime.now());
    }

    private void insertContent(
            String sourceContentId,
            String classificationCode1,
            String classificationCode2,
            double longitude,
            double latitude) {
        jdbcTemplate.update("""
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, region_id,
                    classification_code_1, classification_code_2, address, location,
                    status, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, ?, ?, ?, ?, '서울특별시',
                    ST_SRID(POINT(?, ?), 4326), 'ACTIVE', ?, ?
                )
                """,
                sourceContentId,
                sourceContentId,
                regionId,
                classificationCode1,
                classificationCode2,
                longitude,
                latitude,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
