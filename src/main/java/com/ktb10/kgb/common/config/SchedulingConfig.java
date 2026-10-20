package com.ktb10.kgb.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 주기적인 내부 유지 작업을 실행할 수 있게 Spring Scheduling을 활성화합니다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "kgb.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {
}
