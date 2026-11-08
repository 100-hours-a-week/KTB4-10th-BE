package com.ktb10.kgb.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationCreationBatchMetricsTest {

    @Test
    void recordsResultCountsAndDurationWithFixedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationCreationBatchMetrics metrics =
                new NotificationCreationBatchMetrics(registry);

        metrics.record(
                NotificationCreationProducer.TRIP_STARTING,
                NotificationCreationPhase.RECONCILIATION,
                5,
                2,
                2,
                1,
                Duration.ofMillis(250));

        assertThat(counter(registry, "target")).isEqualTo(5.0);
        assertThat(counter(registry, "created")).isEqualTo(2.0);
        assertThat(counter(registry, "duplicate")).isEqualTo(2.0);
        assertThat(counter(registry, "failed")).isEqualTo(1.0);
        assertThat(registry.get("kgb.notification.creation.batch.duration")
                .tags("producer", "trip_starting", "phase", "reconciliation")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(250.0);
    }

    private double counter(SimpleMeterRegistry registry, String result) {
        return registry.get("kgb.notification.creation.batch.results")
                .tags(
                        "producer", "trip_starting",
                        "phase", "reconciliation",
                        "result", result)
                .counter()
                .count();
    }
}
