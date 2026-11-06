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
class MapGuidebookContentQueryMysqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MapGuidebookContentQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long otherMemberId;
    private Long guidebookId;
    private Long firstContentId;
    private Long secondContentId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void setUp() {
        memberId = insertMember("map-guidebook-member");
        otherMemberId = insertMember("map-guidebook-other-member");
        Long regionId = insertRegion();
        firstContentId = insertContent(regionId, "map-guidebook-content-1");
        secondContentId = insertContent(regionId, "map-guidebook-content-2");
        guidebookId = insertGuidebook(regionId, "지도 일정");
        insertMemberGuidebook(memberId, guidebookId, null);
    }

    @Test
    void returnsDistinctNonNullContentIdsFromActiveStoredGuidebook() {
        Long firstDayId = insertDay(guidebookId, 1, LocalDate.of(2026, 11, 10));
        Long secondDayId = insertDay(guidebookId, 2, LocalDate.of(2026, 11, 11));
        insertItem(firstDayId, firstContentId, 1);
        insertItem(firstDayId, null, 2);
        insertItem(secondDayId, secondContentId, 1);
        insertItem(secondDayId, firstContentId, 2);

        var result = query.findActiveContentIds(memberId, guidebookId);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow())
                .containsExactlyInAnyOrder(firstContentId, secondContentId);
    }

    @Test
    void returnsPresentEmptySetWhenActiveGuidebookHasNoLinkedTourismContent() {
        Long dayId = insertDay(guidebookId, 1, LocalDate.of(2026, 11, 10));
        insertItem(dayId, null, 1);

        var result = query.findActiveContentIds(memberId, guidebookId);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isEmpty();
    }

    @Test
    void returnsEmptyWhenGuidebookIsOwnedByAnotherMemberOrSoftDeleted() {
        assertThat(query.findActiveContentIds(otherMemberId, guidebookId)).isEmpty();

        jdbcTemplate.update(
                "UPDATE member_guidebooks SET deleted_at = ? WHERE member_id = ? AND guidebook_id = ?",
                NOW.plusDays(1), memberId, guidebookId);

        assertThat(query.findActiveContentIds(memberId, guidebookId)).isEmpty();
    }

    private Long insertMember(String oauthSubject) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    oauth_provider, oauth_subject, nickname, language_code,
                    status, push_enabled, created_at, updated_at
                ) VALUES ('KAKAO', ?, '테스트회원', 'ko', 'ACTIVE', TRUE, ?, ?)
                """,
                oauthSubject, NOW, NOW);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE oauth_subject = ?", Long.class, oauthSubject);
    }

    private Long insertRegion() {
        jdbcTemplate.update(
                """
                INSERT INTO regions (administrative_code, name, region_level)
                VALUES ('11', '서울특별시', 'PROVINCE')
                """);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '11'", Long.class);
    }

    private Long insertContent(Long regionId, String sourceContentId) {
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, region_id,
                    classification_code_1, classification_code_2,
                    address, location, status, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, ?, ?, 'NA', 'NA04', '서울특별시',
                    ST_SRID(POINT(126.9780, 37.5665), 4326), 'ACTIVE', ?, ?
                )
                """,
                sourceContentId, sourceContentId, regionId, NOW, NOW);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
    }

    private Long insertGuidebook(Long regionId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO guidebooks (
                    title, region_id, start_date, end_date, companion,
                    people_count, version, created_at, updated_at
                ) VALUES (?, ?, '2026-11-10', '2026-11-11', 'ALONE', 1, 1, ?, ?)
                """,
                title, regionId, NOW, NOW);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM guidebooks WHERE title = ?", Long.class, title);
    }

    private void insertMemberGuidebook(Long ownerId, Long storedGuidebookId, LocalDateTime deletedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO member_guidebooks (
                    member_id, guidebook_id, acquisition_type, created_at, deleted_at
                ) VALUES (?, ?, 'CREATED', ?, ?)
                """,
                ownerId, storedGuidebookId, NOW, deletedAt);
    }

    private Long insertDay(Long storedGuidebookId, int dayNumber, LocalDate date) {
        jdbcTemplate.update(
                """
                INSERT INTO itinerary_days (guidebook_id, day_number, itinerary_date)
                VALUES (?, ?, ?)
                """,
                storedGuidebookId, dayNumber, date);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM itinerary_days WHERE guidebook_id = ? AND day_number = ?",
                Long.class,
                storedGuidebookId,
                dayNumber);
    }

    private void insertItem(Long dayId, Long contentId, int sequence) {
        jdbcTemplate.update(
                """
                INSERT INTO itinerary_items (
                    itinerary_day_id, tourism_content_id, sequence, place_snapshot, created_at
                ) VALUES (?, ?, ?, JSON_OBJECT('name', '테스트 장소'), ?)
                """,
                dayId, contentId, sequence, NOW);
    }
}
