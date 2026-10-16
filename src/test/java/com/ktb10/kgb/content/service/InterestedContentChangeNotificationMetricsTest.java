package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class InterestedContentChangeNotificationMetricsTest {

    @Test
    void recordsFailuresByStage() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        InterestedContentChangeNotificationMetrics metrics =
                new InterestedContentChangeNotificationMetrics(meterRegistry);

        metrics.recordQueueRejected();
        metrics.recordProcessingFailed();
        metrics.recordDeliveryFailed();

        assertThat(failureCount(meterRegistry, "queue_rejected")).isEqualTo(1.0);
        assertThat(failureCount(meterRegistry, "processing")).isEqualTo(1.0);
        assertThat(failureCount(meterRegistry, "delivery")).isEqualTo(1.0);
    }

    private double failureCount(SimpleMeterRegistry meterRegistry, String stage) {
        return meterRegistry.get("kgb.content.change_notification.failures")
                .tag("stage", stage)
                .counter()
                .count();
    }
}
