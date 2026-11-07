package com.ktb10.kgb.guidebook.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class GenerationRabbitTopologyIntegrationTest {

    private static final String USERNAME = "kgb-test";
    private static final String PASSWORD = "kgb-test-password";

    @Container
    private static final GenericContainer<?> RABBITMQ = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:4-management"))
            .withEnv("RABBITMQ_DEFAULT_USER", USERNAME)
            .withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD)
            .withExposedPorts(5672);

    private CachingConnectionFactory connections;
    private RabbitTemplate rabbit;

    @BeforeEach
    void setUp() {
        connections = new CachingConnectionFactory(
                RABBITMQ.getHost(), RABBITMQ.getMappedPort(5672));
        connections.setUsername(USERNAME);
        connections.setPassword(PASSWORD);
        connections.setPublisherConfirmType(ConfirmType.CORRELATED);
        connections.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connections);
        rabbit.setMandatory(true);

        GenerationRabbitConfiguration configuration =
                new GenerationRabbitConfiguration(Duration.ofMillis(500), 10_000);
        RabbitAdmin admin = new RabbitAdmin(connections);
        admin.declareExchange(configuration.guidebookGenerationExchange());
        admin.declareExchange(configuration.guidebookGenerationRetryExchange());
        admin.declareExchange(configuration.guidebookGenerationDeadLetterExchange());
        admin.declareQueue(configuration.guidebookGenerationQueue());
        admin.declareQueue(configuration.guidebookGenerationRetryQueue());
        admin.declareQueue(configuration.guidebookGenerationDeadLetterQueue());
        admin.declareBinding(configuration.guidebookGenerationBinding(
                configuration.guidebookGenerationQueue(),
                configuration.guidebookGenerationExchange()));
        admin.declareBinding(configuration.guidebookGenerationRetryBinding(
                configuration.guidebookGenerationRetryQueue(),
                configuration.guidebookGenerationRetryExchange()));
        admin.declareBinding(configuration.guidebookGenerationDeadLetterBinding(
                configuration.guidebookGenerationDeadLetterQueue(),
                configuration.guidebookGenerationDeadLetterExchange()));
    }

    @AfterEach
    void tearDown() {
        connections.destroy();
    }

    @Test
    void rejectedGenerationReturnsToMainQueueAfterRetryDelay() throws Exception {
        String payload = "{\"messageId\":\"generation-123-1\"}";
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("generation-123-1");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        CorrelationData correlation = new CorrelationData("generation-123-1");

        rabbit.send(
                GenerationRabbitConfiguration.EXCHANGE,
                GenerationRabbitConfiguration.ROUTING_KEY,
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties),
                correlation);

        assertThat(correlation.getFuture().get(5, TimeUnit.SECONDS).isAck()).isTrue();
        rejectWithoutRequeue();

        Message retried = rabbit.receive(GenerationRabbitConfiguration.QUEUE, 10_000);

        assertThat(retried).isNotNull();
        assertThat(new String(retried.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
        assertThat(retried.getMessageProperties().getHeaders()).containsKey("x-death");
    }

    private void rejectWithoutRequeue() throws Exception {
        Connection connection = connections.createConnection();
        Channel channel = connection.createChannel(false);
        try {
            GetResponse delivery = channel.basicGet(GenerationRabbitConfiguration.QUEUE, false);
            assertThat(delivery).isNotNull();
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
        } finally {
            channel.close();
            connection.close();
        }
    }
}
