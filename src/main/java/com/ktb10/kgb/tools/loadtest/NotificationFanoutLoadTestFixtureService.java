package com.ktb10.kgb.tools.loadtest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여러 알림 생산자를 같은 조건으로 측정하기 위한 로컬 전용 관계 fixture를 관리합니다. */
@Service
@Profile("local & loadtest")
public class NotificationFanoutLoadTestFixtureService {

    static final String SOURCE_CONTENT_ID = "loadtest-notification-event";
    private static final String REGION_CODE = "99";
    private static final String GUIDEBOOK_TITLE = "알림부하테스트";
    private static final String PUSH_ENDPOINT_PREFIX = "https://push.loadtest.local/";
    private static final String LOAD_TEST_MEMBER_PREFIX = "load-test-user-%";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public NotificationFanoutLoadTestFixtureService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public PreparedFixture prepare(
            NotificationFanoutLoadTestScenario scenario,
            int memberCount,
            boolean pushSubscriptions) {
        validateMemberCount(memberCount);
        cleanup();
        configureTargetMembers(memberCount);

        LocalDate today = LocalDate.now(clock.withZone(SEOUL_ZONE));
        UUID runId = UUID.randomUUID();
        long regionId = prepareRegion();
        long contentId = prepareEvent(regionId, today);
        prepareSyncRun(runId, today);
        prepareChangeOccurrence(runId);

        Long guidebookId = null;
        if (needsFavorite(scenario)) {
            prepareFavorites(contentId, memberCount);
        }
        if (needsGuidebook(scenario)) {
            guidebookId = prepareGuidebook(regionId, today);
            prepareMemberGuidebooks(guidebookId, memberCount);
            if (needsGuidebookEvent(scenario)) {
                prepareItinerary(guidebookId, contentId, today);
            }
        }
        if (pushSubscriptions) {
            preparePushSubscriptions(memberCount);
        }

        return new PreparedFixture(
                scenario, memberCount, runId, contentId, guidebookId, pushSubscriptions);
    }

    @Transactional(readOnly = true)
    public FixtureStatus status() {
        Map<String, Long> notifications =
                groupedCounts(
                        """
                        SELECT notification.type, COUNT(*) AS count
                        FROM notifications notification
                        JOIN members member ON member.id = notification.recipient_member_id
                        WHERE member.oauth_subject LIKE ?
                        GROUP BY notification.type
                        ORDER BY notification.type
                        """);
        Map<String, Long> outbox =
                groupedCounts(
                        """
                        SELECT outbox.status, COUNT(*) AS count
                        FROM web_push_outbox outbox
                        JOIN members member ON member.id = outbox.recipient_member_id
                        WHERE member.oauth_subject LIKE ?
                        GROUP BY outbox.status
                        ORDER BY outbox.status
                        """);
        return new FixtureStatus(
                count(
                        "SELECT COUNT(*) FROM members WHERE oauth_subject LIKE ?",
                        LOAD_TEST_MEMBER_PREFIX),
                count(
                        """
                        SELECT COUNT(*)
                        FROM favorite_contents favorite
                        JOIN tourism_contents content ON content.id = favorite.content_id
                        WHERE content.source_content_id = ?
                        """,
                        SOURCE_CONTENT_ID),
                count(
                        """
                        SELECT COUNT(*)
                        FROM member_guidebooks member_guidebook
                        JOIN guidebooks guidebook ON guidebook.id = member_guidebook.guidebook_id
                        WHERE guidebook.title = ?
                        """,
                        GUIDEBOOK_TITLE),
                count(
                        """
                        SELECT COUNT(*)
                        FROM notification_occurrences occurrence
                        JOIN members member ON member.id = occurrence.recipient_member_id
                        WHERE member.oauth_subject LIKE ?
                        """,
                        LOAD_TEST_MEMBER_PREFIX),
                notifications,
                outbox);
    }

    @Transactional
    public void cleanup() {
        jdbcTemplate.update(
                """
                DELETE outbox
                FROM web_push_outbox outbox
                JOIN members member ON member.id = outbox.recipient_member_id
                WHERE member.oauth_subject LIKE ?
                """,
                LOAD_TEST_MEMBER_PREFIX);
        jdbcTemplate.update(
                """
                DELETE notification
                FROM notifications notification
                JOIN members member ON member.id = notification.recipient_member_id
                WHERE member.oauth_subject LIKE ?
                """,
                LOAD_TEST_MEMBER_PREFIX);
        jdbcTemplate.update(
                """
                DELETE occurrence
                FROM notification_occurrences occurrence
                JOIN members member ON member.id = occurrence.recipient_member_id
                WHERE member.oauth_subject LIKE ?
                """,
                LOAD_TEST_MEMBER_PREFIX);
        jdbcTemplate.update(
                "DELETE FROM web_push_subscriptions WHERE endpoint LIKE ?",
                PUSH_ENDPOINT_PREFIX + "%");
        jdbcTemplate.update(
                """
                DELETE favorite
                FROM favorite_contents favorite
                JOIN tourism_contents content ON content.id = favorite.content_id
                WHERE content.source_content_id = ?
                """,
                SOURCE_CONTENT_ID);
        jdbcTemplate.update(
                """
                DELETE item
                FROM itinerary_items item
                JOIN itinerary_days day ON day.id = item.itinerary_day_id
                JOIN guidebooks guidebook ON guidebook.id = day.guidebook_id
                WHERE guidebook.title = ?
                """,
                GUIDEBOOK_TITLE);
        jdbcTemplate.update(
                """
                DELETE day
                FROM itinerary_days day
                JOIN guidebooks guidebook ON guidebook.id = day.guidebook_id
                WHERE guidebook.title = ?
                """,
                GUIDEBOOK_TITLE);
        jdbcTemplate.update(
                """
                DELETE member_guidebook
                FROM member_guidebooks member_guidebook
                JOIN guidebooks guidebook ON guidebook.id = member_guidebook.guidebook_id
                WHERE guidebook.title = ?
                """,
                GUIDEBOOK_TITLE);
        jdbcTemplate.update("DELETE FROM guidebooks WHERE title = ?", GUIDEBOOK_TITLE);
        jdbcTemplate.update(
                """
                DELETE occurrence
                FROM tour_api_content_change_occurrences occurrence
                WHERE occurrence.source_content_id = ?
                """,
                SOURCE_CONTENT_ID);
        jdbcTemplate.update(
                """
                DELETE sync_run
                FROM tour_api_sync_runs sync_run
                WHERE sync_run.sync_date = CURRENT_DATE()
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tour_api_content_change_occurrences occurrence
                      WHERE occurrence.run_id = sync_run.run_id
                  )
                """);
        jdbcTemplate.update(
                """
                DELETE detail
                FROM event_details detail
                JOIN tourism_contents content ON content.id = detail.content_id
                WHERE content.source_content_id = ?
                """,
                SOURCE_CONTENT_ID);
        jdbcTemplate.update(
                "DELETE FROM tourism_contents WHERE source_content_id = ?", SOURCE_CONTENT_ID);
        jdbcTemplate.update(
                "UPDATE members SET status = 'ACTIVE' WHERE oauth_subject LIKE ?",
                LOAD_TEST_MEMBER_PREFIX);
    }

    private void validateMemberCount(int memberCount) {
        if (memberCount < 1 || memberCount > 10_000) {
            throw new IllegalArgumentException("알림 부하테스트 회원 수는 1~10000이어야 합니다.");
        }
        long available =
                count(
                        "SELECT COUNT(*) FROM members WHERE oauth_subject LIKE ?",
                        LOAD_TEST_MEMBER_PREFIX);
        if (available < memberCount) {
            throw new IllegalArgumentException(
                    "준비된 부하테스트 회원이 부족합니다. requested=" + memberCount + ", available=" + available);
        }
    }

    private long prepareRegion() {
        jdbcTemplate.update(
                """
                INSERT INTO regions (administrative_code, name, region_level)
                VALUES (?, '부하테스트', 'PROVINCE')
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """,
                REGION_CODE);
        return requiredLong("SELECT id FROM regions WHERE administrative_code = ?", REGION_CODE);
    }

    private void configureTargetMembers(int memberCount) {
        jdbcTemplate.update(
                "UPDATE members SET status = 'ONBOARDING' WHERE oauth_subject LIKE ?",
                LOAD_TEST_MEMBER_PREFIX);
        jdbcTemplate.update(
                """
                UPDATE members member
                JOIN (
                    SELECT id
                    FROM members
                    WHERE oauth_subject LIKE ?
                    ORDER BY id
                    LIMIT ?
                ) selected ON selected.id = member.id
                SET member.status = 'ACTIVE'
                """,
                LOAD_TEST_MEMBER_PREFIX,
                memberCount);
    }

    private long prepareEvent(long regionId, LocalDate today) {
        LocalDateTime createdAt = today.atTime(1, 0);
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, source_content_type_id,
                    title, description, region_id,
                    source_region_code, source_district_code,
                    classification_code_1, classification_code_2, classification_code_3,
                    address, location, phone, homepage_url, thumbnail_url,
                    source_created_at, source_modified_at,
                    status, deleted_at, created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, '15',
                    '알림 부하테스트 행사', '알림 fan-out 전용 fixture', ?,
                    'LT', '001', 'EV', 'EV01', 'EV010100',
                    '부하테스트 주소', ST_SRID(POINT(126.978, 37.566), 4326),
                    NULL, NULL, NULL,
                    ?, ?,
                    'ACTIVE', NULL, ?, ?
                )
                """,
                SOURCE_CONTENT_ID,
                regionId,
                createdAt,
                createdAt,
                createdAt,
                createdAt);
        long contentId =
                requiredLong(
                        "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                        SOURCE_CONTENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO event_details (
                    content_id, start_date, end_date,
                    operating_hours, organizer, organizer_phone, event_place, updated_at
                ) VALUES (?, ?, ?, '09:00~18:00', 'KGB', NULL, '부하테스트 장소', UTC_TIMESTAMP(6))
                """,
                contentId,
                today.plusDays(1),
                today.plusDays(2));
        return contentId;
    }

    private void prepareSyncRun(UUID runId, LocalDate today) {
        jdbcTemplate.update(
                """
                DELETE occurrence
                FROM tour_api_content_change_occurrences occurrence
                JOIN tour_api_sync_runs sync_run ON sync_run.run_id = occurrence.run_id
                WHERE sync_run.sync_date = ?
                """,
                today);
        jdbcTemplate.update("DELETE FROM tour_api_sync_runs WHERE sync_date = ?", today);
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_sync_runs (
                    run_id, sync_date, status, attempt_count,
                    source_contents, imported_contents, inactive_contents,
                    source_events, imported_events,
                    started_at, completed_at, created_at, updated_at
                ) VALUES (?, ?, 'SUCCESS', 1, 1, 1, 0, 1, 1,
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                runId.toString(),
                today);
    }

    private void prepareChangeOccurrence(UUID runId) {
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_content_change_occurrences (
                    run_id, source_provider, source_content_id,
                    lifecycle_change, changed_fields_json,
                    created_at, updated_at
                ) VALUES (?, 'TOUR_API', ?, 'NONE', JSON_ARRAY('START_DATE'),
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                runId.toString(),
                SOURCE_CONTENT_ID);
    }

    private void prepareFavorites(long contentId, int memberCount) {
        jdbcTemplate.update(
                """
                INSERT INTO favorite_contents (member_id, content_id, created_at)
                SELECT selected.id, ?, UTC_TIMESTAMP(6)
                FROM (
                    SELECT id
                    FROM members
                    WHERE oauth_subject LIKE ?
                    ORDER BY id
                    LIMIT ?
                ) selected
                """,
                contentId,
                LOAD_TEST_MEMBER_PREFIX,
                memberCount);
    }

    private long prepareGuidebook(long regionId, LocalDate today) {
        jdbcTemplate.update(
                """
                INSERT INTO guidebooks (
                    title, region_id, start_date, end_date,
                    companion, people_count, thumbnail_url, content_html,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'FRIEND', 2, NULL, '<html>loadtest</html>',
                          1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                GUIDEBOOK_TITLE,
                regionId,
                today.plusDays(1),
                today.plusDays(2));
        return requiredLong("SELECT id FROM guidebooks WHERE title = ?", GUIDEBOOK_TITLE);
    }

    private void prepareMemberGuidebooks(long guidebookId, int memberCount) {
        jdbcTemplate.update(
                """
                INSERT INTO member_guidebooks (
                    member_id, guidebook_id, acquisition_type, created_at, deleted_at
                )
                SELECT selected.id, ?, 'CREATED', UTC_TIMESTAMP(6), NULL
                FROM (
                    SELECT id
                    FROM members
                    WHERE oauth_subject LIKE ?
                    ORDER BY id
                    LIMIT ?
                ) selected
                """,
                guidebookId,
                LOAD_TEST_MEMBER_PREFIX,
                memberCount);
    }

    private void prepareItinerary(long guidebookId, long contentId, LocalDate today) {
        jdbcTemplate.update(
                """
                INSERT INTO itinerary_days (guidebook_id, day_number, itinerary_date)
                VALUES (?, 1, ?)
                """,
                guidebookId,
                today.plusDays(1));
        long dayId =
                requiredLong(
                        "SELECT id FROM itinerary_days WHERE guidebook_id = ? AND day_number = 1",
                        guidebookId);
        jdbcTemplate.update(
                """
                INSERT INTO itinerary_items (
                    itinerary_day_id, tourism_content_id, sequence,
                    scheduled_time, place_snapshot, created_at
                ) VALUES (?, ?, 1, '10:00:00', JSON_OBJECT('title', '알림 부하테스트 행사'),
                          UTC_TIMESTAMP(6))
                """,
                dayId,
                contentId);
    }

    private void preparePushSubscriptions(int memberCount) {
        jdbcTemplate.update(
                """
                INSERT INTO web_push_subscriptions (
                    member_id, session_hash, endpoint, endpoint_hash,
                    p256dh, auth_secret, expiration_at, status,
                    failure_count, created_at, updated_at
                )
                SELECT selected.id,
                       LOWER(SHA2(CONCAT('loadtest-session-', selected.id), 256)),
                       CONCAT(?, selected.id),
                       UNHEX(SHA2(CONCAT(?, selected.id), 256)),
                       'loadtest-p256dh', 'loadtest-auth', NULL, 'ACTIVE',
                       0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM (
                    SELECT id
                    FROM members
                    WHERE oauth_subject LIKE ?
                    ORDER BY id
                    LIMIT ?
                ) selected
                """,
                PUSH_ENDPOINT_PREFIX,
                PUSH_ENDPOINT_PREFIX,
                LOAD_TEST_MEMBER_PREFIX,
                memberCount);
    }

    private boolean needsFavorite(NotificationFanoutLoadTestScenario scenario) {
        return scenario == NotificationFanoutLoadTestScenario.INTERESTED_EVENT_CHANGE
                || scenario == NotificationFanoutLoadTestScenario.MIXED;
    }

    private boolean needsGuidebook(NotificationFanoutLoadTestScenario scenario) {
        return scenario == NotificationFanoutLoadTestScenario.GUIDEBOOK_EVENT_CHANGE
                || scenario == NotificationFanoutLoadTestScenario.TRIP_STARTING
                || scenario == NotificationFanoutLoadTestScenario.MIXED;
    }

    private boolean needsGuidebookEvent(NotificationFanoutLoadTestScenario scenario) {
        return scenario == NotificationFanoutLoadTestScenario.GUIDEBOOK_EVENT_CHANGE
                || scenario == NotificationFanoutLoadTestScenario.MIXED;
    }

    private Map<String, Long> groupedCounts(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<Map.Entry<String, Long>> rows =
                jdbcTemplate.query(
                        sql,
                        (resultSet, rowNumber) ->
                                Map.entry(resultSet.getString(1), resultSet.getLong(2)),
                        LOAD_TEST_MEMBER_PREFIX);
        for (Map.Entry<String, Long> row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return Map.copyOf(result);
    }

    private long count(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : value;
    }

    private long requiredLong(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        if (value == null) {
            throw new IllegalStateException("알림 부하테스트 fixture 식별자를 조회하지 못했습니다.");
        }
        return value;
    }

    public record PreparedFixture(
            NotificationFanoutLoadTestScenario scenario,
            int memberCount,
            UUID runId,
            long contentId,
            Long guidebookId,
            boolean pushSubscriptions) {}

    public record FixtureStatus(
            long availableMembers,
            long favorites,
            long memberGuidebooks,
            long occurrences,
            Map<String, Long> notifications,
            Map<String, Long> outbox) {

        public FixtureStatus {
            notifications = Map.copyOf(notifications);
            outbox = Map.copyOf(outbox);
        }
    }
}
