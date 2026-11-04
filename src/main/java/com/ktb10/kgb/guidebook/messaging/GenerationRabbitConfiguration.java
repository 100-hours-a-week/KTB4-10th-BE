package com.ktb10.kgb.guidebook.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.generation.outbox-enabled", havingValue = "true")
class GenerationRabbitConfiguration {

    static final String QUEUE = "guidebook.generation";

    @Bean
    Queue guidebookGenerationQueue() {
        return QueueBuilder.durable(QUEUE).quorum().build();
    }
}
