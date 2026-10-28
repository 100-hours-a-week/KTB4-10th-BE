package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MemberSchemaMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void appliesAllFlywayMigrationsAndValidatesJpaSchema() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void rejectsDuplicateOauthIdentity() {
        insertMember("duplicate-subject");

        assertThatThrownBy(() -> insertMember("duplicate-subject"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void serviceSessionTableHasBeenRemoved() {
        assertThat(jdbcTemplate.queryForList("SHOW TABLES LIKE 'auth_sessions'")).isEmpty();
    }

    @Test
    void rejectsDuplicatePreferenceAndUnknownMemberReference() {
        long memberId = insertMember("preference-member");
        insertPreference(memberId, "THEME", "NATURE");

        assertThatThrownBy(() -> insertPreference(memberId, "THEME", "NATURE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPreference(Long.MAX_VALUE, "THEME", "HISTORY"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidNotificationMemberAndReferencePair() {
        long memberId = insertMember("notification-member");

        assertThatThrownBy(() -> insertNotification(Long.MAX_VALUE, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertNotification(memberId, "GUIDEBOOK", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_notifications_reference_pair");
    }

    @Test
    void enforcesWebPushEndpointUniquenessAndReferences() {
        long memberId = insertMember("web-push-schema-member");
        String sessionHash = "a".repeat(64);
        byte[] endpointHash = filledHash((byte) 2);
        insertWebPushSubscription(memberId, sessionHash, endpointHash);

        assertThatThrownBy(() -> insertWebPushSubscription(memberId, sessionHash, endpointHash))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertWebPushSubscription(
                Long.MAX_VALUE,
                sessionHash,
                filledHash((byte) 3)))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertWebPushSubscription(memberId, "b".repeat(64), filledHash((byte) 4));
    }

    @Test
    void createsWebPushOutboxWithUniqueDeliveryKeyAndNoSourceForeignKeys() {
        long memberId = insertMember("web-push-outbox-schema-member");
        insertWebPushSubscription(memberId, "c".repeat(64), filledHash((byte) 5));
        long notificationId = insertNotificationReturningId(
                memberId,
                "GUIDEBOOK",
                "outbox-schema-guidebook");
        long subscriptionId = jdbcTemplate.queryForObject(
                "SELECT id FROM web_push_subscriptions WHERE member_id = ?",
                Long.class,
                memberId);

        insertWebPushOutbox(notificationId, subscriptionId);

        assertThatThrownBy(() -> insertWebPushOutbox(notificationId, subscriptionId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'web_push_outbox'
                """,
                Integer.class)).isZero();
    }

    private long insertMember(String oauthSubject) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO members (
                        oauth_provider, oauth_subject, nickname, language_code,
                        status, push_enabled, created_at, updated_at
                    ) VALUES (?, ?, ?, 'ko', 'ACTIVE', TRUE, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
            statement.setString(1, "KAKAO");
            statement.setString(2, oauthSubject);
            statement.setString(3, "테스트회원");
            statement.setObject(4, now);
            statement.setObject(5, now);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertPreference(long memberId, String type, String code) {
        jdbcTemplate.update(
                """
                INSERT INTO member_preferences (member_id, preference_type, preference_code)
                VALUES (?, ?, ?)
                """,
                memberId,
                type,
                code);
    }

    private void insertNotification(
            long memberId,
            String referenceType,
            String referenceId) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    recipient_member_id, type, title, body,
                    reference_type, reference_id, created_at
                ) VALUES (?, 'GUIDEBOOK_COMPLETED', '완료', '완료 본문', ?, ?,
                          '2026-09-27 12:00:00')
                """,
                memberId,
                referenceType,
                referenceId);
    }

    private long insertNotificationReturningId(
            long memberId,
            String referenceType,
            String referenceId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO notifications (
                        recipient_member_id, type, title, body,
                        reference_type, reference_id, created_at
                    ) VALUES (?, 'GUIDEBOOK_COMPLETED', '완료', '완료 본문', ?, ?,
                              '2026-09-27 12:00:00')
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, referenceType);
            statement.setString(3, referenceId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertWebPushOutbox(long notificationId, long subscriptionId) {
        jdbcTemplate.update(
                """
                INSERT INTO web_push_outbox (
                    notification_id, recipient_member_id, subscription_id,
                    payload_json, status,
                    attempt_count, next_attempt_at, created_at, updated_at
                ) VALUES (?, ?, ?, '{}', 'PENDING', 0,
                          '2026-09-27 12:00:00', '2026-09-27 12:00:00',
                          '2026-09-27 12:00:00')
                """,
                notificationId,
                jdbcTemplate.queryForObject(
                        "SELECT recipient_member_id FROM notifications WHERE id = ?",
                        Long.class,
                        notificationId),
                subscriptionId);
    }

    private void insertWebPushSubscription(
            long memberId,
            String sessionHash,
            byte[] endpointHash) {
        jdbcTemplate.update(
                """
                INSERT INTO web_push_subscriptions (
                    member_id, session_hash, endpoint, endpoint_hash,
                    p256dh, auth_secret, status, failure_count, created_at, updated_at
                ) VALUES (?, ?, 'https://push.example.test/subscription', ?,
                          'p256dh', 'auth', 'ACTIVE', 0,
                          '2026-09-27 12:00:00', '2026-09-27 12:00:00')
                """,
                memberId,
                sessionHash,
                endpointHash);
    }

    private byte[] filledHash(byte value) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, value);
        return hash;
    }
}
