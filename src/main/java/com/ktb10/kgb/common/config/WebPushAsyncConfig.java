package com.ktb10.kgb.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Web Push 네트워크 전송을 요청 처리 스레드와 분리된 제한 큐에서 실행합니다. */
@Configuration
public class WebPushAsyncConfig {

    @Bean(name = "webPushTaskExecutor")
    public Executor webPushTaskExecutor(
            @Value("${webpush.executor.core-pool-size:2}") int corePoolSize,
            @Value("${webpush.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${webpush.executor.queue-capacity:100}") int queueCapacity) {
        if (corePoolSize <= 0 || maxPoolSize < corePoolSize || queueCapacity <= 0) {
            throw new IllegalArgumentException("Web Push 실행기 설정이 올바르지 않습니다.");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("web-push-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
