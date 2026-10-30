package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPushOutboxMetricsTest {

    @Test
    @DisplayName("선점과 발송 및 batch 처리 메트릭을 기록한다")
    void recordsWorkerProcessingMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebPushOutboxMetrics metrics = new WebPushOutboxMetrics(
                registry,
                mock(WebPushOutboxRepository.class),
                Clock.systemUTC());

        assertThat(metrics.recordClaim(() -> "claimed")).isEqualTo("claimed");
        metrics.recordDelivery(() -> { });
        var sample = metrics.startBatch();
        metrics.stopBatch(sample);
        metrics.recordClaimedBatchSize(10);
        metrics.recordWorkerError();

        assertThat(registry.get("kgb.notification.webpush.outbox.claim.duration")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("kgb.notification.webpush.outbox.delivery.duration")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("kgb.notification.webpush.outbox.batch.duration")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("kgb.notification.webpush.outbox.claimed.batch.size")
                .summary().totalAmount()).isEqualTo(10);
        assertThat(registry.get("kgb.notification.webpush.outbox.worker.errors")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("timeout batch의 101초 처리 시간을 histogram 구간에 기록한다")
    void recordsLongRunningBatchWithinHistogramRange() {
        MockClock meterClock = new MockClock();
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT,
                new PrometheusRegistry(),
                meterClock);
        WebPushOutboxMetrics metrics = new WebPushOutboxMetrics(
                registry,
                mock(WebPushOutboxRepository.class),
                Clock.systemUTC());

        var sample = metrics.startBatch();
        meterClock.add(Duration.ofSeconds(101));
        metrics.stopBatch(sample);

        assertThat(registry.scrape())
                .contains("kgb_notification_webpush_outbox_batch_duration_seconds_bucket{le=\"120.0\"} 1");
    }
}
