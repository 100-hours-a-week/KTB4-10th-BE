package com.ktb10.kgb.member.session;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis에서 세션만 검증하고 활동 시각을 갱신합니다. 회원 상태는 저장하지 않습니다. */
public final class RedisServiceSessionAuthenticationStore implements ServiceSessionAuthenticationStore {

    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final DefaultRedisScript<String> AUTHENTICATE = new DefaultRedisScript<>();

    static {
        AUTHENTICATE.setLocation(new ClassPathResource("redis/service-session/authenticate-and-record-use.lua"));
        AUTHENTICATE.setResultType(String.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final String namespace;

    public RedisServiceSessionAuthenticationStore(StringRedisTemplate redisTemplate, String namespace) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Redis namespace must not be blank");
        }
        this.namespace = namespace;
    }

    @Override
    public Optional<Long> authenticateAndRecordUse(byte[] sessionIdHash, Duration idleTimeout) {
        if (sessionIdHash == null || sessionIdHash.length != 32) {
            throw new IllegalArgumentException("Session hash must contain 32 bytes");
        }
        Objects.requireNonNull(idleTimeout);
        long idleMillis = idleTimeout.toMillis();
        if (idleMillis <= 0 || idleMillis > MAX_SAFE_INTEGER
                || !idleTimeout.equals(Duration.ofMillis(idleMillis))) {
            throw new IllegalArgumentException("Idle timeout must be positive whole milliseconds");
        }
        String key = namespace + ":session:" + HexFormat.of().formatHex(sessionIdHash);
        String result = Objects.requireNonNull(redisTemplate.execute(
                AUTHENTICATE, List.of(key), Long.toString(idleMillis)), "Missing Redis script result");
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(result));
    }
}
