package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.guidebook.dto.response.GenerationStatusResponse;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class GenerationStreamServiceTest {

    @Test
    void snapshotUsesNewestProgressAndNeverRegressesAfterTerminal() {
        GenerationStreamService streams = new GenerationStreamService(10, Duration.ofMinutes(15));
        streams.publish(update(2, GenerationStatus.PROCESSING, 0));
        streams.publish(update(1, GenerationStatus.PROCESSING, 0));
        streams.publish(update(0, GenerationStatus.FAILED, 0));
        streams.publish(update(5, GenerationStatus.PROCESSING, 0));
        var latest = (com.github.benmanes.caffeine.cache.Cache<Long, GenerationUpdate>)
                ReflectionTestUtils.getField(streams, "latest");
        assertThat(latest.getIfPresent(301L).status()).isEqualTo(GenerationStatus.FAILED);
        streams.publish(update(1, GenerationStatus.PROCESSING, 1));
        assertThat(latest.getIfPresent(301L).requestId()).isEqualTo("301-1");
    }

    @Test
    void enforcesConnectionLimitAndSessionDisconnectFreesIt() {
        GenerationStreamService streams = new GenerationStreamService(1, Duration.ofMinutes(15));
        streams.subscribe(1L, "session-one", 301L, () -> snapshot(GenerationStatus.PENDING));
        assertThatThrownBy(() -> streams.subscribe(1L, "session-two", 301L,
                () -> snapshot(GenerationStatus.PENDING)))
                .isInstanceOf(com.ktb10.kgb.common.error.BusinessException.class);
        streams.disconnect(1L, "different-session");
        var connections = (Map<?, ?>) ReflectionTestUtils.getField(streams, "connections");
        assertThat(connections).hasSize(1);
        streams.disconnect(1L, "session-one");
        assertThat(connections).isEmpty();
        streams.close();
    }

    @Test
    void terminalSnapshotDoesNotLeaveConnectionOpen() {
        GenerationStreamService streams = new GenerationStreamService(1, Duration.ofMinutes(15));
        streams.subscribe(1L, "session-one", 301L, () -> snapshot(GenerationStatus.COMPLETED));
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(streams, "connections")).isEmpty();
    }

    @Test
    void redisFailureDoesNotThrowOrFallbackToOnlyLocalSubscribers() {
        GenerationStreamService streams = mock(GenerationStreamService.class);
        GenerationTimeoutRegistry timers = mock(GenerationTimeoutRegistry.class);
        org.springframework.beans.factory.ObjectProvider<
                org.springframework.data.redis.core.StringRedisTemplate> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.data.redis.core.StringRedisTemplate redis =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.mockito.Mockito.when(provider.getObject()).thenReturn(redis);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redis).convertAndSend(any(), any());
        var relay = new GenerationUpdateRelay(streams, timers, provider,
                new com.fasterxml.jackson.databind.ObjectMapper(), true, "test:generation");
        relay.relay(update(3, GenerationStatus.COMPLETED, 0));
        verify(timers).cancel("301-0");
        verify(streams, never()).publish(any());
    }

    private GenerationUpdate update(long sequence, GenerationStatus status, int attempt) {
        return new GenerationUpdate(1L, 301L, "301-" + attempt, sequence, status,
                status == GenerationStatus.PROCESSING
                        ? new GenerationCallbackEnvelope.Progress("writing", "작성 중") : null,
                null, null);
    }

    private GenerationStatusResponse snapshot(GenerationStatus status) {
        return new GenerationStatusResponse(301L, JobType.INITIAL, status, null, 0, null, null);
    }
}
