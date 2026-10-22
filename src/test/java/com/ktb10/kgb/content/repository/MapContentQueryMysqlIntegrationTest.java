package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void mapsPlaceWithoutEventDetailsAndMapsEventOverviewFields() {
        insertContent("place-overview", "NA", "NA04", 126.98, 37.56);
        jdbcTemplate.update("""
                UPDATE tourism_contents
                SET description = ?, phone = ?, homepage_url = ?
                WHERE source_content_id = ?
                """, "공원 소개", "02-111-2222", "https://place.example.com", "place-overview");

        insertContent("event-overview", "EV", null, 127.02, 37.60);
        Long eventContentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                "event-overview");
        jdbcTemplate.update("""
                UPDATE tourism_contents
                SET description = ?, phone = ?, homepage_url = ?
                WHERE id = ?
                """, "축제 소개", "02-333-4444", "https://event.example.com", eventContentId);
        jdbcTemplate.update("""
                INSERT INTO event_details (
                    content_id, start_date, end_date, operating_hours,
                    organizer, organizer_phone, event_place, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventContentId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "10:00~20:00",
                "서울시",
                "02-555-6666",
                "서울광장",
                LocalDateTime.now());

        List<MapContentCommonData> contents = query.findWithinBounds(
                37.50, 126.90, 37.70, 127.10);

        MapContentCommonData place = contents.stream()
                .filter(content -> content.contentId().equals("place-overview"))
                .findFirst()
                .orElseThrow();
        assertThat(place.overview()).isEqualTo("공원 소개");
        assertThat(place.phone()).isEqualTo("02-111-2222");
        assertThat(place.homepageUrl()).isEqualTo("https://place.example.com");
        assertThat(place.eventDetail()).isNull();

        MapContentCommonData event = contents.stream()
                .filter(content -> content.contentId().equals("event-overview"))
                .findFirst()
                .orElseThrow();
        assertThat(event.overview()).isEqualTo("축제 소개");
        assertThat(event.phone()).isEqualTo("02-333-4444");
        assertThat(event.homepageUrl()).isEqualTo("https://event.example.com");
        assertThat(event.eventDetail()).isNotNull();
        assertThat(event.eventDetail().operatingHours()).isEqualTo("10:00~20:00");
        assertThat(event.eventDetail().organizer()).isEqualTo("서울시");
        assertThat(event.eventDetail().organizerPhone()).isEqualTo("02-555-6666");
        assertThat(event.eventDetail().eventPlace()).isEqualTo("서울광장");
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
