package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SessionSchemaMigrationMysqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void backfillsExistingPushSessionHashesWithoutDeletingSubscriptions() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("13").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.update("""
                INSERT INTO members (id, oauth_provider, oauth_subject, nickname, status, created_at, updated_at)
                VALUES (7, 'KAKAO', 'migration', '회원', 'ACTIVE', NOW(), NOW())
                """);
        for (int i = 1; i <= 2; i++) {
            String hash = (i == 1 ? "01" : "02").repeat(32);
            jdbc.update("""
                    INSERT INTO auth_sessions (id, member_id, session_id_hash, expires_at, created_at)
                    VALUES (?, 7, UNHEX(?), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY))
                    """, i, hash);
            jdbc.update("""
                    INSERT INTO web_push_subscriptions
                        (member_id, auth_session_id, endpoint, endpoint_hash, p256dh, auth_secret,
                         status, created_at, updated_at)
                    VALUES (7, ?, ?, UNHEX(?), 'key', 'secret', ?, NOW(), NOW())
                    """, i, "https://fcm.googleapis.com/" + i, hash, i == 1 ? "ACTIVE" : "REVOKED");
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        assertThat(jdbc.queryForList("SHOW TABLES LIKE 'auth_sessions'")).isEmpty();
        assertThat(jdbc.queryForList("SELECT session_hash FROM web_push_subscriptions ORDER BY id", String.class))
                .containsExactly("01".repeat(32), "02".repeat(32));
        assertThat(jdbc.queryForList("SELECT status FROM web_push_subscriptions ORDER BY id", String.class))
                .containsExactly("ACTIVE", "REVOKED");
    }
}
