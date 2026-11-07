package com.ktb10.kgb.guidebook.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

class GenerationRabbitConfigurationTest {

    private final GenerationRabbitConfiguration configuration =
            new GenerationRabbitConfiguration(Duration.ofSeconds(30), 10_000);

    @Test
    void mainQueueRoutesRejectedMessagesToRetryQueue() {
        Queue queue = configuration.guidebookGenerationQueue();
        Binding binding = configuration.guidebookGenerationBinding(
                queue, configuration.guidebookGenerationExchange());

        assertThat(queue.getName()).isEqualTo(GenerationRabbitConfiguration.QUEUE);
        assertThat(queue.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-max-length", 10_000L)
                .containsEntry("x-overflow", "reject-publish")
                .containsEntry("x-dead-letter-strategy", "at-least-once")
                .containsEntry("x-dead-letter-exchange", GenerationRabbitConfiguration.RETRY_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", GenerationRabbitConfiguration.RETRY_ROUTING_KEY);
        assertThat(binding.getExchange()).isEqualTo(GenerationRabbitConfiguration.EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(GenerationRabbitConfiguration.ROUTING_KEY);
    }

    @Test
    void retryQueueReturnsExpiredMessagesToMainExchange() {
        Queue queue = configuration.guidebookGenerationRetryQueue();
        Binding binding = configuration.guidebookGenerationRetryBinding(
                queue, configuration.guidebookGenerationRetryExchange());

        assertThat(queue.getArguments())
                .containsEntry("x-message-ttl", 30_000)
                .containsEntry("x-max-length", 10_000L)
                .containsEntry("x-overflow", "reject-publish")
                .containsEntry("x-dead-letter-strategy", "at-least-once")
                .containsEntry("x-dead-letter-exchange", GenerationRabbitConfiguration.EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", GenerationRabbitConfiguration.ROUTING_KEY);
        assertThat(binding.getExchange()).isEqualTo(GenerationRabbitConfiguration.RETRY_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(GenerationRabbitConfiguration.RETRY_ROUTING_KEY);
    }

    @Test
    void deadLetterQueueHasDedicatedExchangeAndRoutingKey() {
        Queue queue = configuration.guidebookGenerationDeadLetterQueue();
        Binding binding = configuration.guidebookGenerationDeadLetterBinding(
                queue, configuration.guidebookGenerationDeadLetterExchange());

        assertThat(queue.getName()).isEqualTo(GenerationRabbitConfiguration.DEAD_LETTER_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(GenerationRabbitConfiguration.DEAD_LETTER_EXCHANGE);
        assertThat(binding.getRoutingKey())
                .isEqualTo(GenerationRabbitConfiguration.DEAD_LETTER_ROUTING_KEY);
    }
}
