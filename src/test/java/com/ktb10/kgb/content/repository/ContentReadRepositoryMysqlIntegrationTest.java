package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.content.service.ContentReadService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
class ContentReadRepositoryMysqlIntegrationTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 16, 10, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ContentReadRepository repository;

    @Autowired
    private ContentReadService service;

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
        jdbcTemplate.update(
                """
                INSERT INTO regions (administrative_code, name, region_level)
                VALUES ('11', '서울특별시', 'PROVINCE')
                """);
        regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '11'",
                Long.class);
    }

    @Test
    void queriesEventPagesAndCombinesDetailImagesOnMysql() {
        insertEvent("event-old", "부산 불꽃축제", "ACTIVE", null, BASE_TIME);
        insertEvent("event-other", "제주 문화행사", "ACTIVE", null, BASE_TIME.plusHours(1));
        insertEvent("event-seoul-2", "서울 음악회", "ACTIVE", null, BASE_TIME.plusHours(2));
        Long detailId = insertEvent(
                "event-seoul-1", "서울 야행", "ACTIVE", null, BASE_TIME.plusHours(3));
        insertEvent("event-inactive", "서울 종료 행사", "INACTIVE", null,
                BASE_TIME.plusHours(4));
        insertEvent("event-deleted", "서울 삭제 행사", "ACTIVE", BASE_TIME.plusDays(1),
                BASE_TIME.plusHours(5));
        insertImages(detailId);

        var firstPage = repository.findEvents(null, PageRequest.of(0, 2));
        var secondPage = repository.findEvents(null, PageRequest.of(1, 2));
        var searchResult = repository.findEvents("서울", PageRequest.of(0, 10));
        var detail = service.getEventDetail("event-seoul-1");

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getContent())
                .extracting(ContentReadRepository.ContentListProjection::getContentId)
                .containsExactly("event-seoul-1", "event-seoul-2");
        assertThat(secondPage.getContent())
                .extracting(ContentReadRepository.ContentListProjection::getContentId)
                .containsExactly("event-other", "event-old");
        assertThat(searchResult.getContent())
                .extracting(ContentReadRepository.ContentListProjection::getContentId)
                .containsExactly("event-seoul-1", "event-seoul-2");
        assertThat(repository.findEventDetail("event-inactive")).isEmpty();
        assertThat(repository.findEventDetail("event-deleted")).isEmpty();
        assertThat(detail.title()).isEqualTo("서울 야행");
        assertThat(detail.eventDetail().operatingHours()).isEqualTo("18:00~22:00");
        assertThat(detail.images())
                .extracting(image -> image.imageUrl())
                .containsExactly(
                        "https://example.com/first.jpg",
                        "https://example.com/second.jpg");
    }

    private Long insertEvent(
            String sourceContentId,
            String title,
            String status,
            LocalDateTime deletedAt,
            LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, description, region_id,
                    classification_code_1, address, location, phone, homepage_url,
                    thumbnail_url, status, deleted_at, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, ?, '행사 소개', ?, 'EV', '서울특별시 종로구',
                    ST_SRID(POINT(126.9780, 37.5665), 4326), '02-1234-5678',
                    'https://example.com', 'https://example.com/thumbnail.jpg',
                    ?, ?, ?, ?
                )
                """,
                sourceContentId,
                title,
                regionId,
                status,
                deletedAt,
                createdAt,
                createdAt);
        Long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
        jdbcTemplate.update(
                """
                INSERT INTO event_details (
                    content_id, start_date, end_date, operating_hours, organizer, updated_at
                ) VALUES (?, '2026-10-01', '2026-10-31', '18:00~22:00', '서울시', ?)
                """,
                contentId,
                createdAt);
        return contentId;
    }

    private void insertImages(Long contentId) {
        insertImage(contentId, "https://example.com/second.jpg", 2);
        insertImage(contentId, "https://example.com/first.jpg", 1);
    }

    private void insertImage(Long contentId, String imageUrl, int sortOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO content_images (
                    content_id, image_url, image_url_hash, sort_order, created_at
                ) VALUES (?, ?, UNHEX(SHA2(?, 256)), ?, ?)
                """,
                contentId,
                imageUrl,
                imageUrl,
                sortOrder,
                BASE_TIME);
    }
}
