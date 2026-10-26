package com.ktb10.kgb.member.session;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 회원별 한 세션을 원자적으로 발급·교체·폐기합니다. */
public final class RedisServiceSessionLifecycleStore implements ServiceSessionLifecycleStore {

    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final DefaultRedisScript<String> ISSUE = script("issue-replacing-for-member.lua");
    private static final DefaultRedisScript<String> REVOKE = script("revoke.lua");

    private final StringRedisTemplate redisTemplate;
    private final String sessionPrefix;
    private final String memberPrefix;

    public RedisServiceSessionLifecycleStore(StringRedisTemplate redisTemplate, String namespace) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Redis namespace must not be blank");
        }
        this.sessionPrefix = namespace + ":session:";
        this.memberPrefix = namespace + ":member-session:";
    }

    @Override
    public IssueResult issueReplacingForMember(byte[] sessionIdHash, Long memberId,
            Duration absoluteLifetime, Duration idleTimeout) {
        final String hash = hashHex(sessionIdHash);
        requirePositive(memberId);
        String result = execute(ISSUE, List.of(sessionPrefix + hash, memberKey(memberId)),
                sessionPrefix, hash, memberId.toString(),
                durationMillis(absoluteLifetime), durationMillis(idleTimeout));
        String[] fields = result.split(":", -1);
        if (fields.length != 3) {
            throw new IllegalStateException("Invalid Redis session issuance result");
        }
        return new IssueResult(Instant.ofEpochMilli(Long.parseLong(fields[0])),
                Instant.ofEpochMilli(Long.parseLong(fields[1])),
                fields[2].isEmpty() ? Optional.empty() : Optional.of(fields[2]));
    }

    @Override
    public boolean revokeCurrent(byte[] sessionIdHash, Long memberId) {
        String hash = hashHex(sessionIdHash);
        requirePositive(memberId);
        return !execute(REVOKE, List.of(memberKey(memberId)),
                sessionPrefix, memberId.toString(), hash).isEmpty();
    }

    @Override
    public Optional<String> revokeAllForMember(Long memberId) {
        requirePositive(memberId);
        String result = execute(REVOKE, List.of(memberKey(memberId)),
                sessionPrefix, memberId.toString());
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private String memberKey(Long memberId) {
        return memberPrefix + memberId;
    }

    private String execute(DefaultRedisScript<String> script, List<String> keys, String... arguments) {
        return Objects.requireNonNull(redisTemplate.execute(script, keys, (Object[]) arguments),
                "Missing Redis script result");
    }

    private static DefaultRedisScript<String> script(String name) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/service-session/" + name));
        script.setResultType(String.class);
        return script;
    }

    private static String hashHex(byte[] hash) {
        if (hash == null || hash.length != 32) {
            throw new IllegalArgumentException("Session hash must contain 32 bytes");
        }
        return HexFormat.of().formatHex(hash);
    }

    private static void requirePositive(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Member ID must be positive");
        }
    }

    private static String durationMillis(Duration duration) {
        Objects.requireNonNull(duration);
        long millis = duration.toMillis();
        if (millis <= 0 || millis > MAX_SAFE_INTEGER || !duration.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException("Session duration must be positive whole milliseconds");
        }
        return Long.toString(millis);
    }
}
