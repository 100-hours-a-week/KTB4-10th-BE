package com.ktb10.kgb.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.content.dto.MapContentItemResponse;
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
class FavoriteContentMysqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 12, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private FavoriteContentQuery favoriteContentQuery;

    @Autowired
    private MapContentQuery mapContentQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long otherMemberId;
    private Long contentId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void setUp() {
        memberId = insertMember("favorite-member");
        otherMemberId = insertMember("other-member");
        Long regionId = insertRegion();
        contentId = insertContent(regionId);
    }

    @Test
    void insertIgnoreAndUniqueConstraintKeepSingleFavorite() {
        favoriteContentQuery.save(memberId, contentId, NOW);
        favoriteContentQuery.save(memberId, contentId, NOW.plusSeconds(1));

        assertThat(favoriteCount(memberId, contentId)).isOne();
    }

    @Test
    void joinDeleteRemovesOnlyRequestedMembersFavoriteAndIsIdempotent() {
        favoriteContentQuery.save(memberId, contentId, NOW);
        favoriteContentQuery.save(otherMemberId, contentId, NOW);

        favoriteContentQuery.delete(memberId, "content-1");
        favoriteContentQuery.delete(memberId, "content-1");

        assertThat(favoriteCount(memberId, contentId)).isZero();
        assertThat(favoriteCount(otherMemberId, contentId)).isOne();
    }

    @Test
    void mapLeftJoinReturnsFavoriteStateForEachMember() {
        favoriteContentQuery.save(memberId, contentId, NOW);

        List<MapContentItemResponse> favoriteResult = mapContentQuery.findWithinBounds(
                memberId, 37.39, 127.10, 37.40, 127.12);
        List<MapContentItemResponse> otherResult = mapContentQuery.findWithinBounds(
                otherMemberId, 37.39, 127.10, 37.40, 127.12);

        assertThat(favoriteResult).singleElement()
                .satisfies(item -> assertThat(item.favorite()).isTrue());
        assertThat(otherResult).singleElement()
                .satisfies(item -> assertThat(item.favorite()).isFalse());
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
                "SELECT id FROM members WHERE oauth_subject = ?",
                Long.class,
                oauthSubject);
    }

    private Long insertRegion() {
        jdbcTemplate.update(
                """
                INSERT INTO regions (administrative_code, name, region_level)
                VALUES ('41', '경기도', 'PROVINCE')
                """);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM regions WHERE administrative_code = '41'",
                Long.class);
    }

    private Long insertContent(Long regionId) {
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, title, region_id,
                    classification_code_1, address, location, thumbnail_url,
                    status, created_at, updated_at
                ) VALUES (
                    'TOUR_API', 'content-1', '판교 테스트 장소', ?,
                    'NA', '경기도 성남시', ST_SRID(POINT(127.1105, 37.3953), 4326),
                    'https://example.com/image.jpg', 'ACTIVE', ?, ?
                )
                """,
                regionId, NOW, NOW);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = 'content-1'",
                Long.class);
    }

    private int favoriteCount(Long targetMemberId, Long targetContentId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM favorite_contents
                WHERE member_id = ? AND content_id = ?
                """,
                Integer.class,
                targetMemberId,
                targetContentId);
    }
}
