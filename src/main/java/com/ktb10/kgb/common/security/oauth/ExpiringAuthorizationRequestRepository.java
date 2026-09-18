package com.ktb10.kgb.common.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** 브라우저별 OAuth 로그인 시도를 10분 동안 최대 5개 보관합니다. */
public class ExpiringAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final int MAX_ATTEMPTS = 5;
    static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);
    static final String SESSION_ATTRIBUTE =
            ExpiringAuthorizationRequestRepository.class.getName() + ".AUTHORIZATION_REQUESTS";

    private final Clock clock;

    public ExpiringAuthorizationRequestRepository(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "시계는 null일 수 없습니다.");
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String state = request.getParameter("state");
        if (session == null || state == null) {
            return null;
        }

        synchronized (session) {
            Map<String, StoredAuthorizationRequest> attempts = attempts(session);
            removeExpired(attempts);
            StoredAuthorizationRequest stored = attempts.get(state);
            return stored == null ? null : stored.authorizationRequest();
        }
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;
        }

        String state = Objects.requireNonNull(
                authorizationRequest.getState(),
                "OAuth state는 null일 수 없습니다.");
        HttpSession session = request.getSession(true);
        synchronized (session) {
            Map<String, StoredAuthorizationRequest> attempts = attempts(session);
            removeExpired(attempts);
            while (attempts.size() >= MAX_ATTEMPTS) {
                removeOldest(attempts);
            }
            Instant createdAt = clock.instant();
            attempts.put(
                    state,
                    new StoredAuthorizationRequest(
                            authorizationRequest,
                            createdAt,
                            createdAt.plus(ATTEMPT_TTL)));
            session.setAttribute(SESSION_ATTRIBUTE, attempts);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        String state = request.getParameter("state");
        if (session == null || state == null) {
            return null;
        }

        synchronized (session) {
            Map<String, StoredAuthorizationRequest> attempts = attempts(session);
            removeExpired(attempts);
            StoredAuthorizationRequest removed = attempts.remove(state);
            if (attempts.isEmpty()) {
                session.removeAttribute(SESSION_ATTRIBUTE);
            } else {
                session.setAttribute(SESSION_ATTRIBUTE, attempts);
            }
            return removed == null ? null : removed.authorizationRequest();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, StoredAuthorizationRequest> attempts(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof Map<?, ?>) {
            return (Map<String, StoredAuthorizationRequest>) value;
        }
        return new LinkedHashMap<>();
    }

    private void removeExpired(Map<String, StoredAuthorizationRequest> attempts) {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static void removeOldest(Map<String, StoredAuthorizationRequest> attempts) {
        attempts.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().createdAt()))
                .map(Map.Entry::getKey)
                .ifPresent(attempts::remove);
    }

    private record StoredAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            Instant createdAt,
            Instant expiresAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
