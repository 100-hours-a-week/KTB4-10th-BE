package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.event.NotificationRealtimeEventType;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Redis fan-out과 로컬 SSE 전달 결과를 Prometheus에서 확인할 수 있게 기록합니다. */
@Component
public class NotificationRealtimeMetrics {

    private static final String REDIS_EVENTS_METRIC = "kgb.notification.redis.events";
    private static final String SSE_DELIVERY_METRIC = "kgb.notification.sse.delivery";
    private static final String SSE_CONNECTION_EVENTS_METRIC =
            "kgb.notification.sse.connection.events";
    private static final String SSE_CONNECTION_DURATION_METRIC =
            "kgb.notification.sse.connection.duration";

    private final MeterRegistry meterRegistry;

    public NotificationRealtimeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRedisPublish(
            NotificationRealtimeEventType eventType,
            String outcome) {
        meterRegistry.counter(
                REDIS_EVENTS_METRIC,
                "operation", "publish",
                "event_type", eventType.name().toLowerCase(),
                "outcome", outcome)
                .increment();
    }

    public void recordRedisSubscribe(
            NotificationRealtimeEventType eventType,
            String outcome) {
        String eventTypeTag = eventType == null ? "unknown" : eventType.name().toLowerCase();
        meterRegistry.counter(
                REDIS_EVENTS_METRIC,
                "operation", "subscribe",
                "event_type", eventTypeTag,
                "outcome", outcome)
                .increment();
    }

    public void recordSseDelivery(String outcome) {
        meterRegistry.counter(SSE_DELIVERY_METRIC, "outcome", outcome).increment();
    }

    public void recordSseConnectionOpened() {
        meterRegistry.counter(
                SSE_CONNECTION_EVENTS_METRIC,
                "event", "opened",
                "reason", "none")
                .increment();
    }

    public void recordSseConnectionClosed(
            SseConnectionCloseReason reason,
            long lifetimeNanos) {
        String reasonTag = reason.name().toLowerCase(Locale.ROOT);
        meterRegistry.counter(
                SSE_CONNECTION_EVENTS_METRIC,
                "event", "closed",
                "reason", reasonTag)
                .increment();
        meterRegistry.timer(SSE_CONNECTION_DURATION_METRIC, "reason", reasonTag)
                .record(Duration.ofNanos(Math.max(0L, lifetimeNanos)));
    }
}
