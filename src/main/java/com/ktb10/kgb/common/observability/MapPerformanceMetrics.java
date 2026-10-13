package com.ktb10.kgb.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 지도 조회의 주요 처리 단계별 실행 시간을 기록합니다. */
@Component
public class MapPerformanceMetrics {

    private final Timer commonQuery;
    private final Timer personalizationQuery;
    private final Timer responseMapping;

    public MapPerformanceMetrics(MeterRegistry meterRegistry) {
        this.commonQuery = timer(meterRegistry, "common_query");
        this.personalizationQuery = timer(meterRegistry, "personalization_query");
        this.responseMapping = timer(meterRegistry, "response_mapping");
    }

    public <T> T recordCommonQuery(Supplier<T> operation) {
        return commonQuery.record(operation);
    }

    public <T> T recordPersonalizationQuery(Supplier<T> operation) {
        return personalizationQuery.record(operation);
    }

    public <T> T recordResponseMapping(Supplier<T> operation) {
        return responseMapping.record(operation);
    }

    private Timer timer(MeterRegistry meterRegistry, String stage) {
        return Timer.builder("kgb.map.stage.duration")
                .description("지도 조회 단계별 실행 시간")
                .tag("stage", stage)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
