package com.ktb10.kgb.common.security.oauth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** OAuth 요청을 state별 Redis key로 저장하고 callback에서 원자적으로 소비합니다. */
public class RedisAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final int MAX_ATTEMPTS = 5;
    static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);

    private static final String ATTEMPT_SEGMENT = ":attempt:";
    private static final String INDEX_SEGMENT = ":index:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Clock clock;
    private final String namespace;

    public RedisAuthorizationRequestRepository(
            RedisTemplate<String, Object> redisTemplate,
            Clock clock,
            String namespace) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.clock = Objects.requireNonNull(clock);
        this.namespace = requireText(namespace, "OAuth Redis namespace");
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        RequestKey requestKey = requestKey(request);
        if (requestKey == null) {
            return null;
        }

        Object value = redisTemplate.opsForValue().get(requestKey.attemptKey());
        return authorizationRequest(value, requestKey.state());
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String state = requireText(authorizationRequest.getState(), "OAuth state");
        HttpSession session = request.getSession(true);
        String sessionHash = hash(session.getId());
        String stateHash = hash(state);
        String attemptKey = attemptKey(sessionHash, stateHash);
        String indexKey = indexKey(sessionHash);
        Instant createdAt = clock.instant();

        redisTemplate.opsForValue().set(
                attemptKey,
                new StoredAuthorizationRequest(authorizationRequest, createdAt),
                ATTEMPT_TTL);
        redisTemplate.opsForZSet().add(indexKey, stateHash, createdAt.toEpochMilli());
        redisTemplate.expire(indexKey, ATTEMPT_TTL);

        removeExpiredIndexEntries(indexKey, createdAt);
        trimOldestAttempts(sessionHash, indexKey);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        RequestKey requestKey = requestKey(request);
        if (requestKey == null) {
            return null;
        }

        Object value = redisTemplate.opsForValue().getAndDelete(requestKey.attemptKey());
        redisTemplate.opsForZSet().remove(requestKey.indexKey(), requestKey.stateHash());
        deleteEmptyIndex(requestKey.indexKey());
        return authorizationRequest(value, requestKey.state());
    }

    public void deleteAllForSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        String sessionHash = hash(session.getId());
        String indexKey = indexKey(sessionHash);
        Set<Object> stateHashes = redisTemplate.opsForZSet().range(indexKey, 0, -1);
        if (stateHashes != null) {
            for (Object stateHash : stateHashes) {
                redisTemplate.delete(attemptKey(sessionHash, stateHash.toString()));
            }
        }
        redisTemplate.delete(indexKey);
    }

    private void removeExpiredIndexEntries(String indexKey, Instant now) {
        double expiredAt = now.minus(ATTEMPT_TTL).toEpochMilli();
        redisTemplate.opsForZSet()
                .removeRangeByScore(indexKey, Double.NEGATIVE_INFINITY, expiredAt);
    }

    private void trimOldestAttempts(String sessionHash, String indexKey) {
        Long size = redisTemplate.opsForZSet().zCard(indexKey);
        if (size == null || size <= MAX_ATTEMPTS) {
            return;
        }

        long removeCount = size - MAX_ATTEMPTS;
        Set<Object> oldestStates = redisTemplate.opsForZSet()
                .range(indexKey, 0, removeCount - 1);
        if (oldestStates == null || oldestStates.isEmpty()) {
            return;
        }

        for (Object oldestState : oldestStates) {
            String stateHash = oldestState.toString();
            redisTemplate.delete(attemptKey(sessionHash, stateHash));
            redisTemplate.opsForZSet().remove(indexKey, stateHash);
        }
    }

    private void deleteEmptyIndex(String indexKey) {
        Long size = redisTemplate.opsForZSet().zCard(indexKey);
        if (size != null && size == 0L) {
            redisTemplate.delete(indexKey);
        }
    }

    private OAuth2AuthorizationRequest authorizationRequest(Object value, String expectedState) {
        if (!(value instanceof StoredAuthorizationRequest stored)) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = stored.authorizationRequest();
        if (!Objects.equals(expectedState, authorizationRequest.getState())) {
            return null;
        }
        return authorizationRequest;
    }

    private RequestKey requestKey(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String state = request.getParameter("state");
        if (session == null || state == null || state.isBlank()) {
            return null;
        }

        String sessionHash = hash(session.getId());
        String stateHash = hash(state);
        return new RequestKey(
                state,
                stateHash,
                attemptKey(sessionHash, stateHash),
                indexKey(sessionHash));
    }

    private String attemptKey(String sessionHash, String stateHash) {
        return namespace + ATTEMPT_SEGMENT + sessionHash + ":" + stateHash;
    }

    private String indexKey(String sessionHash) {
        return namespace + INDEX_SEGMENT + sessionHash;
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다.");
        }
        return value;
    }

    private record RequestKey(
            String state,
            String stateHash,
            String attemptKey,
            String indexKey) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    record StoredAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            Instant createdAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
