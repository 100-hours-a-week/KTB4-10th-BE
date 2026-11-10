package com.ktb10.kgb.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 알림 생산자별 최초 실행·제한 재실행의 결과와 처리 시간을 공통 형식으로 기록합니다. */
@Component
public class NotificationCreationBatchMetrics {

    private static final String RESULT_METRIC = "kgb.notification.creation.batch.results";
    private static final String DURATION_METRIC = "kgb.notification.creation.batch.duration";

    private final MeterRegistry meterRegistry;

    public NotificationCreationBatchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(
            NotificationCreationProducer producer,
            NotificationCreationPhase phase,
            int targets,
            int created,
            int duplicate,
            int failed,
            Duration elapsed) {
        increment(producer, phase, "target", targets);
        increment(producer, phase, "created", created);
        increment(producer, phase, "duplicate", duplicate);
        increment(producer, phase, "failed", failed);
        Timer.builder(DURATION_METRIC)
                .description("알림 생성 배치 처리 시간")
                .tags("producer", producer.tagValue(), "phase", phase.tagValue())
                .register(meterRegistry)
                .record(elapsed);
    }

    private void increment(
            NotificationCreationProducer producer,
            NotificationCreationPhase phase,
            String result,
            int count) {
        meterRegistry.counter(
                        RESULT_METRIC,
                        "producer", producer.tagValue(),
                        "phase", phase.tagValue(),
                        "result", result)
                .increment(count);
    }
}
