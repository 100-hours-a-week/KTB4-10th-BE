package com.ktb10.kgb.common.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class ExpiringAuthorizationRequestRepositoryTest {

    private static final Instant START = Instant.parse("2026-09-17T00:00:00Z");

    @Test
    void keepsAtMostFiveAttemptsAndRemovesOldest() {
        MutableClock clock = new MutableClock(START);
        ExpiringAuthorizationRequestRepository repository =
                new ExpiringAuthorizationRequestRepository(clock);
        MockHttpSession session = new MockHttpSession();

        for (int index = 0; index < 6; index++) {
            repository.saveAuthorizationRequest(
                    authorizationRequest("state-" + index),
                    request(session, null),
                    new MockHttpServletResponse());
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(repository.loadAuthorizationRequest(request(session, "state-0"))).isNull();
        assertThat(repository.loadAuthorizationRequest(request(session, "state-1"))).isNotNull();
        assertThat(repository.loadAuthorizationRequest(request(session, "state-5"))).isNotNull();
    }

    @Test
    void expiresAttemptAfterTenMinutes() {
        MutableClock clock = new MutableClock(START);
        ExpiringAuthorizationRequestRepository repository =
                new ExpiringAuthorizationRequestRepository(clock);
        MockHttpSession session = new MockHttpSession();

        repository.saveAuthorizationRequest(
                authorizationRequest("expiring-state"),
                request(session, null),
                new MockHttpServletResponse());
        clock.advance(Duration.ofMinutes(10));

        assertThat(repository.loadAuthorizationRequest(request(session, "expiring-state"))).isNull();
    }

    @Test
    void consumesOnlyMatchingStateOnce() {
        ExpiringAuthorizationRequestRepository repository =
                new ExpiringAuthorizationRequestRepository(Clock.fixed(START, ZoneOffset.UTC));
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                authorizationRequest("first-state"),
                request(session, null),
                response);
        repository.saveAuthorizationRequest(
                authorizationRequest("second-state"),
                request(session, null),
                response);

        assertThat(repository.removeAuthorizationRequest(request(session, "first-state"), response))
                .isNotNull();
        assertThat(repository.removeAuthorizationRequest(request(session, "first-state"), response))
                .isNull();
        assertThat(repository.loadAuthorizationRequest(request(session, "second-state")))
                .isNotNull();
    }

    private static OAuth2AuthorizationRequest authorizationRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/api/v1/auth/oauth/callback/kakao")
                .state(state)
                .build();
    }

    private static MockHttpServletRequest request(MockHttpSession session, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        if (state != null) {
            request.setParameter("state", state);
        }
        return request;
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
