package com.ktb10.kgb.support;

import com.ktb10.kgb.member.entity.Member;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 과거의 고정 Clock 기반 API fixture를 실제 Redis의 상대 시각으로 생성합니다. */
@Component
public class SessionFixtures {
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final String namespace;
    private final Set<String> keys = new HashSet<>();

    public SessionFixtures(StringRedisTemplate redis, Clock clock,
            @Value("${auth.session.redis.namespace}") String namespace) {
        this.redis = redis;
        this.clock = clock;
        this.namespace = namespace;
    }

    public String seed(Member member, byte[] hash, LocalDateTime expires, LocalDateTime created) {
        long now = redis.execute(new DefaultRedisScript<>(
                "local t=redis.call('TIME'); return tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000)",
                Long.class), List.of());
        long offset = now - clock.millis();
        String hex = HexFormat.of().formatHex(hash);
        String key = key(hex);
        String index = namespace + ":member-session:" + member.getId();
        keys.add(key);
        keys.add(index);
        redis.opsForHash().putAll(key, Map.of(
                "schemaVersion", "2", "memberId", member.getId().toString(),
                "createdAtEpochMillis", "" + (created.toInstant(ZoneOffset.UTC).toEpochMilli() + offset),
                "expiresAtEpochMillis", "" + (expires.toInstant(ZoneOffset.UTC).toEpochMilli() + offset),
                "lastUsedAtEpochMillis", "" + (created.toInstant(ZoneOffset.UTC).toEpochMilli() + offset)));
        redis.expire(key, java.time.Duration.ofHours(8));
        redis.opsForValue().set(index, hex, java.time.Duration.ofHours(8));
        return hex;
    }

    public Optional<String> findBySessionIdHash(byte[] hash) {
        String hex = HexFormat.of().formatHex(hash);
        return redis.hasKey(key(hex)) ? Optional.of(hex) : Optional.empty();
    }

    public Optional<String> findById(String hash) {
        return redis.hasKey(key(hash)) ? Optional.of(hash) : Optional.empty();
    }

    public long count() {
        return redis.keys(namespace + ":session:*").size();
    }

    public void clear() {
        // The namespace is owned by this isolated test container, never by a user Redis.
        var testKeys = redis.keys(namespace + ":*");
        if (testKeys != null && !testKeys.isEmpty()) redis.delete(testKeys);
        keys.clear();
    }

    private String key(String hash) {
        return namespace + ":session:" + hash;
    }
}
