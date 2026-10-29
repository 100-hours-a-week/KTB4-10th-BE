package com.ktb10.kgb.guidebook.callback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 결과 DB 커밋 뒤 생성 상태를 로컬 또는 Redis를 통해 SSE로 전달합니다. */
@Component
public class GenerationUpdateRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationUpdateRelay.class);
    private final GenerationStreamService streams;
    private final GenerationTimeoutRegistry timers;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final ObjectMapper json;
    private final boolean redisEnabled;
    private final String channel;

    public GenerationUpdateRelay(
            GenerationStreamService streams,
            GenerationTimeoutRegistry timers,
            ObjectProvider<StringRedisTemplate> redis,
            ObjectMapper json,
            @Value("${ai.generation.redis.enabled:false}") boolean redisEnabled,
            @Value("${ai.generation.redis.channel:kgb:local:generation}") String channel) {
        this.streams = streams;
        this.timers = timers;
        this.redis = redis;
        this.json = json;
        this.redisEnabled = redisEnabled;
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("생성 상태 Redis 채널은 비어 있을 수 없습니다.");
        }
        this.channel = channel;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(GenerationUpdate update) {
        if (update.status() == GenerationStatus.COMPLETED
                || update.status() == GenerationStatus.FAILED
                || update.status() == GenerationStatus.CANCELED) {
            timers.cancel(update.requestId());
        }
        try {
            if (redisEnabled) {
                redis.getObject().convertAndSend(channel, json.writeValueAsString(update));
            } else {
                streams.publish(update);
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.warn("생성 상태 실시간 전달에 실패했습니다. requestId={}",
                    update.requestId(), exception);
        }
    }
}
