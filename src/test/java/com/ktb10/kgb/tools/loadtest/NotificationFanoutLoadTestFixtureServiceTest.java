package com.ktb10.kgb.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

class NotificationFanoutLoadTestFixtureServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NotificationFanoutLoadTestFixtureService service =
            new NotificationFanoutLoadTestFixtureService(jdbcTemplate, Clock.systemUTC());

    @Test
    void rejectsFixtureLargerThanPreparedMemberCount() {
        given(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM members WHERE oauth_subject LIKE ?",
                                Long.class,
                                "load-test-user-%"))
                .willReturn(10L);

        assertThatThrownBy(
                        () -> service.prepare(NotificationFanoutLoadTestScenario.MIXED, 11, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("준비된 부하테스트 회원이 부족합니다");
    }

    @Test
    void rejectsMemberCountOutsideSupportedRangeBeforeDatabaseAccess() {
        assertThatThrownBy(
                        () ->
                                service.prepare(
                                        NotificationFanoutLoadTestScenario.MIXED, 10_001, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~10000");
    }
}
