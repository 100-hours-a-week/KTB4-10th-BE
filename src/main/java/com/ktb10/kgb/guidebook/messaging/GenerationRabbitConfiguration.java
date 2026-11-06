package com.ktb10.kgb.guidebook.messaging;

import java.time.Duration;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.generation.outbox-enabled", havingValue = "true")
class GenerationRabbitConfiguration {

    static final String EXCHANGE = "guidebook.generation.exchange";
    static final String QUEUE = "guidebook.generation";
    static final String ROUTING_KEY = "guidebook.generation";
    static final String RETRY_EXCHANGE = "guidebook.generation.retry.exchange";
    static final String RETRY_QUEUE = "guidebook.generation.retry";
    static final String RETRY_ROUTING_KEY = "guidebook.generation.retry";
    static final String DEAD_LETTER_EXCHANGE = "guidebook.generation.dlx";
    static final String DEAD_LETTER_QUEUE = "guidebook.generation.dlq";
    static final String DEAD_LETTER_ROUTING_KEY = "guidebook.generation.failed";

    private final Duration retryDelay;

    GenerationRabbitConfiguration(
            @Value("${ai.generation.rabbit.retry-delay:30s}") Duration retryDelay) {
        if (retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("RabbitMQ 재시도 지연은 0보다 커야 합니다.");
        }
        this.retryDelay = retryDelay;
    }

    @Bean
    DirectExchange guidebookGenerationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange guidebookGenerationRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange guidebookGenerationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue guidebookGenerationQueue() {
        return QueueBuilder.durable(QUEUE)
                .quorum()
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .deadLetterExchange(RETRY_EXCHANGE)
                .deadLetterRoutingKey(RETRY_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue guidebookGenerationRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .quorum()
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .ttl(Math.toIntExact(retryDelay.toMillis()))
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    Queue guidebookGenerationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    }

    @Bean
    Binding guidebookGenerationBinding(
            @Qualifier("guidebookGenerationQueue") Queue queue,
            @Qualifier("guidebookGenerationExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(ROUTING_KEY);
    }

    @Bean
    Binding guidebookGenerationRetryBinding(
            @Qualifier("guidebookGenerationRetryQueue") Queue queue,
            @Qualifier("guidebookGenerationRetryExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(RETRY_ROUTING_KEY);
    }

    @Bean
    Binding guidebookGenerationDeadLetterBinding(
            @Qualifier("guidebookGenerationDeadLetterQueue") Queue queue,
            @Qualifier("guidebookGenerationDeadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
