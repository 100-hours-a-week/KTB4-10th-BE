package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.guidebook.dto.response.GenerationStatusResponse;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class GenerationStreamServiceTest {

    @Test
    void slowSnapshotDoesNotBlockOtherJobsOrRegressAfterCompletion() throws Exception {
        GenerationStreamService streams = new GenerationStreamService(10, Duration.ofMinutes(15));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var slow = executor.submit(() -> streams.subscribe(1L, "slow", 301L, () -> {
                started.countDown();
                await(release);
                return snapshot(GenerationStatus.PENDING);
            }));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> {
                    streams.subscribe(2L, "other", 302L, () -> snapshot(GenerationStatus.PENDING));
                    streams.heartbeat();
                    streams.disconnect(2L, "other");
                    streams.publish(update(3, GenerationStatus.COMPLETED, 0));
                }).get(5, TimeUnit.SECONDS);
                assertThat(slow.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            slow.get(5, TimeUnit.SECONDS);
            assertThat((Map<?, ?>) ReflectionTestUtils.getField(streams, "connections")).isEmpty();
        } finally {
            streams.close();
        }
    }

    @Test
    void slowSendDoesNotBlockOtherJobsOrReverseEventOrder() throws Exception {
        GenerationStreamService streams = new GenerationStreamService(10, Duration.ofMinutes(15));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var emitters = mockConstruction(SseEmitter.class);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            streams.subscribe(1L, "one", 301L, () -> snapshot(GenerationStatus.PENDING));
            streams.subscribe(2L, "two", 302L, () -> snapshot(GenerationStatus.PENDING));
            SseEmitter slowEmitter = emitters.constructed().getFirst();
            doAnswer(invocation -> {
                started.countDown();
                await(release);
                return null;
            }).when(slowEmitter).send(any(SseEmitter.SseEventBuilder.class));
            var slow = executor.submit(() -> streams.publish(update(1, GenerationStatus.PROCESSING, 0)));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> streams.publish(new GenerationUpdate(
                        2L, 302L, "302-0", 3, GenerationStatus.COMPLETED, null, null, null)))
                        .get(5, TimeUnit.SECONDS);
                verify(emitters.constructed().get(1)).complete();
                assertThat(slow.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            slow.get(5, TimeUnit.SECONDS);
            streams.publish(update(3, GenerationStatus.COMPLETED, 0));
            streams.publish(update(2, GenerationStatus.PROCESSING, 0));
            verify(slowEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
            streams.close();
        }
    }

    @Test
    void concurrentSubscriptionsRespectGlobalAndMemberLimits() throws Exception {
        for (int limit : new int[]{3, 30}) {
            GenerationStreamService streams = new GenerationStreamService(limit, Duration.ofMinutes(15));
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var requests = IntStream.range(0, 20).mapToObj(index -> executor.submit(() -> {
                    await(start);
                    try {
                        streams.subscribe(limit == 3 ? index + 1L : 1L, "session-" + index, 301L,
                                () -> snapshot(GenerationStatus.PENDING));
                        return true;
                    } catch (com.ktb10.kgb.common.error.BusinessException exception) {
                        return false;
                    }
                })).toList();
                start.countDown();
                int accepted = 0;
                for (var request : requests) {
                    if (request.get(5, TimeUnit.SECONDS)) {
                        accepted++;
                    }
                }
                assertThat(accepted).isEqualTo(Math.min(limit, 5));
            } finally {
                streams.close();
            }
        }
    }

    @Test
    void sendAndCompletionFailuresDoNotStopOtherConnections() throws Exception {
        GenerationStreamService streams = new GenerationStreamService(10, Duration.ofMinutes(15));
        try (var emitters = mockConstruction(SseEmitter.class)) {
            for (int index = 0; index < 3; index++) {
                streams.subscribe(1L, "session-" + index, 301L, () -> snapshot(GenerationStatus.PENDING));
            }
            SseEmitter broken = emitters.constructed().getFirst();
            SseEmitter closing = emitters.constructed().get(1);
            SseEmitter healthy = emitters.constructed().get(2);
            doThrow(new IOException("closed")).when(broken).send(any(SseEmitter.SseEventBuilder.class));
            doThrow(new IllegalStateException("already closed")).when(closing).complete();
            streams.publish(update(1, GenerationStatus.PROCESSING, 0));
            streams.heartbeat();
            streams.publish(update(3, GenerationStatus.COMPLETED, 0));
            verify(broken, never()).completeWithError(any());
            verify(healthy, times(4)).send(any(SseEmitter.SseEventBuilder.class));
            verify(healthy).complete();
            assertThat((Map<?, ?>) ReflectionTestUtils.getField(streams, "connections")).isEmpty();
            streams.close();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 테스트 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

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
