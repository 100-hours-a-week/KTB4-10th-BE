package com.ktb10.kgb.guidebook.pdf;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** PDF 변환 결과별 시간과 성공 결과 크기를 기록합니다. */
@Component
public class GuidebookPdfMetrics {

    private final MeterRegistry meterRegistry;
    private final DistributionSummary resultSize;

    public GuidebookPdfMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.resultSize = DistributionSummary.builder("kgb.guidebook.pdf.result.size")
                .baseUnit("bytes")
                .description("생성에 성공한 가이드북 PDF 파일 크기")
                .register(meterRegistry);
    }

    public void record(String result, Duration duration, Integer size) {
        Timer.builder("kgb.guidebook.pdf.duration")
                .description("가이드북 PDF 변환 시간")
                .tag("result", result)
                .register(meterRegistry)
                .record(duration);
        if (size != null) {
            resultSize.record(size);
        }
    }
}
