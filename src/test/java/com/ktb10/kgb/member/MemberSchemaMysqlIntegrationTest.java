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
                Integer.class)).isGreaterThanOrEqualTo(4);
    }

    @Test
    void rejectsDuplicateOauthIdentity() {
        insertMember("duplicate-subject");

        assertThatThrownBy(() -> insertMember("duplicate-subject"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateSessionHashAndUnknownMemberReference() {
        long memberId = insertMember("session-member");
        byte[] sessionHash = new byte[32];
        Arrays.fill(sessionHash, (byte) 1);
        insertSession(memberId, sessionHash);

        assertThatThrownBy(() -> insertSession(memberId, sessionHash))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSession(Long.MAX_VALUE, new byte[32]))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private void insertSession(long memberId, byte[] sessionHash) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_sessions (
                    member_id, session_id_hash, expires_at, last_used_at, created_at
                ) VALUES (?, ?, '2026-09-28 12:00:00', '2026-09-27 12:00:00',
                          '2026-09-27 12:00:00')
                """,
                memberId,
                sessionHash);
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
}
