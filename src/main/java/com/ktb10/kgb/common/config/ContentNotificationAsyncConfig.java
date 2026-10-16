package com.ktb10.kgb.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 관심 행사 변경 알림을 TourAPI 스케줄러와 분리된 제한 큐에서 실행합니다. */
@Configuration
public class ContentNotificationAsyncConfig {

    @Bean(name = "contentNotificationTaskExecutor")
    public Executor contentNotificationTaskExecutor(
            @Value("${content.notification.executor.core-pool-size:1}") int corePoolSize,
            @Value("${content.notification.executor.max-pool-size:1}") int maxPoolSize,
            @Value("${content.notification.executor.queue-capacity:50}") int queueCapacity) {
        if (corePoolSize <= 0 || maxPoolSize < corePoolSize || queueCapacity <= 0) {
            throw new IllegalArgumentException("콘텐츠 알림 실행기 설정이 올바르지 않습니다.");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("content-notification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
