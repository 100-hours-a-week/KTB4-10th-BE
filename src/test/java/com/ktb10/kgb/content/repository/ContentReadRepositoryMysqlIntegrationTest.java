package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.content.model.ContentSearchCategory;
import com.ktb10.kgb.content.service.ContentReadService;
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

    private Long seoulRegionId;

    private Long busanRegionId;

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
                VALUES ('11', '서울특별시', 'PROVINCE'),
                       ('26', '부산광역시', 'PROVINCE')
                """);
        Long seoulProvinceId = findRegionId("11");
        Long busanProvinceId = findRegionId("26");
        jdbcTemplate.update(
                """
                INSERT INTO regions (
                    parent_id, administrative_code, name, region_level
                ) VALUES (?, '11110', '종로구', 'DISTRICT'),
                         (?, '26110', '중구', 'DISTRICT')
                """,
                seoulProvinceId,
                busanProvinceId);
        seoulRegionId = findRegionId("11110");
        busanRegionId = findRegionId("26110");
    }

    private Long findRegionId(String administrativeCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = ?",
                Long.class,
                administrativeCode);
    }

    @Test
    void queriesEventPagesAndCombinesDetailImagesOnMysql() {
        insertEvent("event-old", "부산 불꽃축제", busanRegionId, "ACTIVE", null,
                BASE_TIME, "2099-10-01", "2099-10-31");
        insertEvent("event-other", "제주 문화행사", busanRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(1), "2099-10-01", "2099-10-31");
        insertEvent("event-seoul-2", "서울 음악회", seoulRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(2), "2099-10-01", "2099-10-31");
        Long detailId = insertEvent(
                "event-seoul-1", "서울 야행", seoulRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(3), "2099-10-01", "2099-10-31");
        insertEvent("event-inactive", "서울 종료 행사", seoulRegionId, "INACTIVE", null,
                BASE_TIME.plusHours(4), "2099-10-01", "2099-10-31");
        insertEvent("event-deleted", "서울 삭제 행사", seoulRegionId, "ACTIVE",
                BASE_TIME.plusDays(1), BASE_TIME.plusHours(5),
                "2099-10-01", "2099-10-31");
        insertImages(detailId);
        Long memberId = insertMember("detail-member");
        jdbcTemplate.update(
                """
                INSERT INTO favorite_contents (member_id, content_id, created_at)
                VALUES (?, ?, ?)
                """,
                memberId,
                detailId,
                BASE_TIME);

        var firstPage = service.getContents(
                memberId, ContentSearchCategory.EVENT, null, null, null, 1, 2);
        var secondPage = service.getContents(
                memberId, ContentSearchCategory.EVENT, null, null, null, 2, 2);
        var searchResult = service.getContents(
                memberId, ContentSearchCategory.EVENT, "서울", null, null, 1, 10);
        var detail = service.getEventDetail(memberId, "event-seoul-1");

        assertThat(firstPage.totalItems()).isEqualTo(4);
        assertThat(firstPage.items())
                .extracting(item -> item.contentId())
                .containsExactly("event-seoul-1", "event-seoul-2");
        assertThat(secondPage.items())
                .extracting(item -> item.contentId())
                .containsExactly("event-other", "event-old");
        assertThat(searchResult.items())
                .extracting(item -> item.contentId())
                .containsExactly("event-seoul-1", "event-seoul-2");
        assertThat(searchResult.items().getFirst().favorite()).isTrue();
        assertThat(repository.findEventDetail("event-inactive")).isEmpty();
        assertThat(repository.findEventDetail("event-deleted")).isEmpty();
        assertThat(detail.title()).isEqualTo("서울 야행");
        assertThat(detail.favorite()).isTrue();
        assertThat(detail.eventDetail().operatingHours()).isEqualTo("18:00~22:00");
        assertThat(detail.images())
                .extracting(image -> image.imageUrl())
                .containsExactly(
                        "https://example.com/first.jpg",
                        "https://example.com/second.jpg");
    }

    @Test
    void filtersEventsByProvinceAndOverlappingMonth() {
        insertEvent("seoul-overlap", "가을 야행", seoulRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(1), "2099-09-30", "2099-10-01");
        insertEvent("seoul-november", "겨울 야행", seoulRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(2), "2099-11-01", "2099-11-30");
        insertEvent("busan-october", "가을 야행", busanRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(3), "2099-10-01", "2099-10-31");
        Long memberId = insertMember("filter-member");

        var result = service.getContents(
                memberId,
                ContentSearchCategory.EVENT,
                "야행",
                "11",
                "2099-10",
                1,
                20);

        assertThat(result.items())
                .extracting(item -> item.contentId())
                .containsExactly("seoul-overlap");
        assertThat(result.items().getFirst().region().administrativeCode()).isEqualTo("11");
        assertThat(result.items().getFirst().region().name()).isEqualTo("서울특별시");
        assertThat(repository.countProvinceByCode("11")).isEqualTo(1L);
        assertThat(repository.countProvinceByCode("unknown")).isZero();
    }

    @Test
    void searchesAllowedEventsAndPlacesWithLiteralQueryAndPersonalization() {
        insertEvent("search-event", "공통 검색 행사", seoulRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(1), "2099-10-01", "2099-10-31");
        insertEvent("ended-event", "공통 검색 종료 행사", seoulRegionId, "ACTIVE", null,
                BASE_TIME.plusHours(2), "2020-10-01", "2020-10-31");
        Long placeId = insertPlace(
                "search-place", "공통 검색 장소", seoulRegionId,
                "NA01", BASE_TIME.plusHours(3));
        insertPlace(
                "disallowed-place", "공통 검색 제외", seoulRegionId,
                "VE05", BASE_TIME.plusHours(4));
        insertPlace(
                "literal-place", "100% 명소", seoulRegionId,
                "NA01", BASE_TIME.plusHours(5));
        insertPlace(
                "underscore-place", "under_score 명소", seoulRegionId,
                "NA01", BASE_TIME.plusHours(6));
        insertPlace(
                "quote-place", "따옴표' 명소", seoulRegionId,
                "NA01", BASE_TIME.plusHours(7));
        insertPlace(
                "backslash-place", "역슬래시\\ 명소", seoulRegionId,
                "NA01", BASE_TIME.plusHours(8));
        insertPlace(
                "stable-place-1", "정렬 장소 하나", seoulRegionId,
                "NA01", BASE_TIME.plusHours(9));
        insertPlace(
                "stable-place-2", "정렬 장소 둘", seoulRegionId,
                "NA01", BASE_TIME.plusHours(9));
        Long memberId = insertMember("search-member");
        jdbcTemplate.update(
                "INSERT INTO favorite_contents (member_id, content_id, created_at) VALUES (?, ?, ?)",
                memberId,
                placeId,
                BASE_TIME);
        insertGuidebookContent(memberId, placeId);

        var all = service.getContents(
                memberId, ContentSearchCategory.ALL,
                "공통 검색", null, null, 1, 20);
        var literal = service.getContents(
                memberId, ContentSearchCategory.PLACE,
                "%", null, null, 1, 20);
        var underscore = service.getContents(
                memberId, ContentSearchCategory.PLACE,
                "_", null, null, 1, 20);
        var quote = service.getContents(
                memberId, ContentSearchCategory.PLACE,
                "'", null, null, 1, 20);
        var backslash = service.getContents(
                memberId, ContentSearchCategory.PLACE,
                "\\", null, null, 1, 20);
        var sqlLikeInput = service.getContents(
                memberId, ContentSearchCategory.ALL,
                "' OR 1=1", null, null, 1, 20);
        var stableOrder = service.getContents(
                memberId, ContentSearchCategory.PLACE,
                "정렬 장소", null, null, 1, 20);

        assertThat(all.items())
                .extracting(item -> item.contentId())
                .containsExactly("search-place", "search-event");
        assertThat(all.items().getFirst().contentType()).isEqualTo("PLACE");
        assertThat(all.items().getFirst().eventPeriod()).isNull();
        assertThat(all.items().getFirst().favorite()).isTrue();
        assertThat(all.items().getFirst().inGuidebook()).isTrue();
        assertThat(literal.items())
                .extracting(item -> item.contentId())
                .containsExactly("literal-place");
        assertThat(underscore.items())
                .extracting(item -> item.contentId())
                .containsExactly("underscore-place");
        assertThat(quote.items())
                .extracting(item -> item.contentId())
                .containsExactly("quote-place");
        assertThat(backslash.items())
                .extracting(item -> item.contentId())
                .containsExactly("backslash-place");
        assertThat(sqlLikeInput.items()).isEmpty();
        assertThat(stableOrder.items())
                .extracting(item -> item.contentId())
                .containsExactly("stable-place-2", "stable-place-1");
    }

    private Long insertMember(String oauthSubject) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    oauth_provider, oauth_subject, nickname, language_code,
                    status, push_enabled, created_at, updated_at
                ) VALUES ('KAKAO', ?, '테스트회원', 'ko', 'ACTIVE', TRUE, ?, ?)
                """,
                oauthSubject,
                BASE_TIME,
                BASE_TIME);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE oauth_subject = ?",
                Long.class,
                oauthSubject);
    }

    private Long insertEvent(
            String sourceContentId,
            String title,
            Long contentRegionId,
            String status,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            String startDate,
            String endDate) {
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
                contentRegionId,
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
                ) VALUES (?, ?, ?, '18:00~22:00', '서울시', ?)
                """,
                contentId,
                startDate,
                endDate,
                createdAt);
        return contentId;
    }

    private Long insertPlace(
            String sourceContentId,
            String title,
            Long contentRegionId,
            String classificationCode2,
            LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, description, region_id,
                    classification_code_1, classification_code_2, address, location,
                    thumbnail_url, status, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, ?, '장소 소개', ?, 'NA', ?, '서울특별시 종로구',
                    ST_SRID(POINT(126.9780, 37.5665), 4326),
                    'https://example.com/place.jpg', 'ACTIVE', ?, ?
                )
                """,
                sourceContentId,
                title,
                contentRegionId,
                classificationCode2,
                createdAt,
                createdAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
    }

    private void insertGuidebookContent(Long memberId, Long contentId) {
        jdbcTemplate.update(
                """
                INSERT INTO guidebooks (
                    title, region_id, start_date, end_date, companion,
                    people_count, created_at, updated_at
                ) VALUES ('검색 가이드북', ?, '2099-10-01', '2099-10-01',
                          'ALONE', 1, ?, ?)
                """,
                seoulRegionId,
                BASE_TIME,
                BASE_TIME);
        Long guidebookId = jdbcTemplate.queryForObject(
                "SELECT id FROM guidebooks WHERE title = '검색 가이드북'",
                Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO member_guidebooks (
                    member_id, guidebook_id, acquisition_type, created_at
                ) VALUES (?, ?, 'CREATED', ?)
                """,
                memberId,
                guidebookId,
                BASE_TIME);
        jdbcTemplate.update(
                """
                INSERT INTO itinerary_days (guidebook_id, day_number, itinerary_date)
                VALUES (?, 1, '2099-10-01')
                """,
                guidebookId);
        Long dayId = jdbcTemplate.queryForObject(
                "SELECT id FROM itinerary_days WHERE guidebook_id = ?",
                Long.class,
                guidebookId);
        jdbcTemplate.update(
                """
                INSERT INTO itinerary_items (
                    itinerary_day_id, tourism_content_id, sequence,
                    place_snapshot, created_at
                ) VALUES (?, ?, 1, JSON_OBJECT('title', '공통 검색 장소'), ?)
                """,
                dayId,
                contentId,
                BASE_TIME);
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
