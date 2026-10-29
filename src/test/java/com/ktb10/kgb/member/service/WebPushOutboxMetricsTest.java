package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
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
}
