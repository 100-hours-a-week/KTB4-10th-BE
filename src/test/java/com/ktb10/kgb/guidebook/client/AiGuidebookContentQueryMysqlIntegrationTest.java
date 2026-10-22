package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Content;
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
class AiGuidebookContentQueryMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AiGuidebookContentQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long districtId;

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
        Long provinceId = jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '11'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO regions (parent_id, administrative_code, name, region_level)
                VALUES (?, '11110', '종로구', 'DISTRICT')
                """, provinceId);
        districtId = jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '11110'", Long.class);
    }

    @Test
    void returnsOnlyAllowedCategoriesAndNormalizesOverviewUsingMysqlValues() {
        String overview = "  첫 문장입니다.\n\t" + "가".repeat(105) + ". 이후 문장도 포함됩니다"
                + "나".repeat(100);
        insertContent("nature-park", "NA", "NA04", overview);
        insertContent("urban-park", "VE", "VE03", null);
        insertContent("unused-culture", "VE", "VE05", "제외 대상");
        insertContent("unknown", "ZZ", "ZZ99", "제외 대상");
        insertEvent("event", "행사 개요");

        var result = query.findAll(
                "서울특별시", "종로구", LocalDate.now(), LocalDate.now().plusDays(1));

        assertThat(result)
                .extracting(Content::contentId)
                .containsExactly("nature-park", "urban-park", "event");
        assertThat(result.get(0).overview())
                .isEqualTo("첫 문장입니다. " + "가".repeat(105)
                        + ". 이후 문장도 포함됩니다" + "나".repeat(100));
        assertThat(result.get(1).overview()).isEmpty();
    }

    private void insertEvent(String sourceContentId, String overview) {
        insertContent(sourceContentId, "EV", null, overview);
        Long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
        jdbcTemplate.update("""
                INSERT INTO event_details (content_id, start_date, end_date, updated_at)
                VALUES (?, '2026-10-16', '2026-10-17', ?)
                """, contentId, LocalDateTime.now());
    }

    private void insertContent(
            String sourceContentId,
            String classificationCode1,
            String classificationCode2,
            String overview) {
        jdbcTemplate.update("""
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, description, region_id,
                    classification_code_1, classification_code_2, address, location,
                    status, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, ?, ?, ?, ?, ?, '서울특별시 종로구',
                    ST_SRID(POINT(126.9780, 37.5665), 4326), 'ACTIVE', ?, ?
                )
                """,
                sourceContentId,
                sourceContentId,
                overview,
                districtId,
                classificationCode1,
                classificationCode2,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
