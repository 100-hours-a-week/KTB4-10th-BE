package com.ktb10.kgb.member.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.event.NotificationRealtimeEventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisNotificationRealtimeIntegrationTest {

    private static final String CHANNEL = "kgb:test:notification:sse";
    private static final Instant NOW = Instant.parse("2026-10-20T01:00:00Z");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory publisherConnectionFactory;
    private LettuceConnectionFactory firstSubscriberConnectionFactory;
    private LettuceConnectionFactory secondSubscriberConnectionFactory;
    private RedisMessageListenerContainer firstContainer;
    private RedisMessageListenerContainer secondContainer;
    private NotificationRealtimeAdapter firstAdapter;
    private NotificationRealtimeAdapter secondAdapter;
    private RedisNotificationRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisherConnectionFactory = connectionFactory();
        firstSubscriberConnectionFactory = connectionFactory();
        secondSubscriberConnectionFactory = connectionFactory();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NotificationRealtimeMetrics metrics = new NotificationRealtimeMetrics(
                new SimpleMeterRegistry());
        firstAdapter = mock(NotificationRealtimeAdapter.class);
        secondAdapter = mock(NotificationRealtimeAdapter.class);
        firstContainer = listenerContainer(
                firstSubscriberConnectionFactory,
                new RedisNotificationRealtimeSubscriber(objectMapper, firstAdapter, metrics));
        secondContainer = listenerContainer(
                secondSubscriberConnectionFactory,
                new RedisNotificationRealtimeSubscriber(objectMapper, secondAdapter, metrics));

        StringRedisTemplate redisTemplate = new StringRedisTemplate(publisherConnectionFactory);
        redisTemplate.afterPropertiesSet();
        publisher = new RedisNotificationRealtimePublisher(
                redisTemplate,
                objectMapper,
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC),
                CHANNEL);
    }

    @AfterEach
    void tearDown() {
        firstContainer.stop();
        secondContainer.stop();
        publisherConnectionFactory.destroy();
        firstSubscriberConnectionFactory.destroy();
        secondSubscriberConnectionFactory.destroy();
    }

    @Test
    void fansOutNotificationToSubscribersRepresentingTwoInstances() {
        publisher.publishNotification(notificationEvent());

        verify(firstAdapter, timeout(5_000)).handle(argThat(envelope ->
                envelope.eventType() == NotificationRealtimeEventType.NOTIFICATION_CREATED
                        && envelope.memberId().equals(1L)
                        && envelope.notification().notificationId().equals("301")));
        verify(secondAdapter, timeout(5_000)).handle(argThat(envelope ->
                envelope.eventType() == NotificationRealtimeEventType.NOTIFICATION_CREATED
                        && envelope.memberId().equals(1L)
                        && envelope.notification().notificationId().equals("301")));
    }

    @Test
    void fansOutSessionDisconnectToEveryInstance() {
        publisher.publishSessionDisconnected(1L, 11L);

        verify(firstAdapter, timeout(5_000)).handle(argThat(envelope ->
                envelope.eventType() == NotificationRealtimeEventType.SESSION_DISCONNECTED
                        && envelope.memberId().equals(1L)
                        && envelope.sessionId().equals(11L)));
        verify(secondAdapter, timeout(5_000)).handle(argThat(envelope ->
                envelope.eventType() == NotificationRealtimeEventType.SESSION_DISCONNECTED
                        && envelope.memberId().equals(1L)
                        && envelope.sessionId().equals(11L)));
    }

    private LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private RedisMessageListenerContainer listenerContainer(
            LettuceConnectionFactory connectionFactory,
            RedisNotificationRealtimeSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(CHANNEL));
        container.afterPropertiesSet();
        container.start();
        return container;
    }

    private NotificationCreatedEvent notificationEvent() {
        return new NotificationCreatedEvent(
                1L,
                new NotificationItemResponse(
                        "301",
                        NotificationType.GUIDEBOOK_COMPLETED,
                        "가이드북 생성 완료",
                        "가이드북을 확인해 주세요.",
                        NotificationReferenceType.GUIDEBOOK,
                        "101",
                        OffsetDateTime.parse("2026-10-20T01:00:00Z")),
                true);
    }
}
