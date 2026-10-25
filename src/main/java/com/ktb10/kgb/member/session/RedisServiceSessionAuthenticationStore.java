package com.ktb10.kgb.member.session;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis 인증 어댑터. 발급·폐기 전환 전까지 Spring 기본 빈으로 등록하지 않습니다. */
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
    public Optional<AuthenticatedMember> authenticateAndRecordUse(byte[] sessionIdHash, Duration idleTimeout) {
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
        String[] fields = result.split(":", -1);
        if (fields.length != 3) {
            throw new IllegalStateException("Invalid Redis authentication result");
        }
        return Optional.of(new AuthenticatedMember(
                Long.valueOf(fields[1]), MemberStatus.valueOf(fields[2]), Long.valueOf(fields[0])));
    }
}
