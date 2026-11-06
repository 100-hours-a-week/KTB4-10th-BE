package com.ktb10.kgb.guidebook.messaging;

import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "ai.generation.outbox-enabled", havingValue = "true")
public class GenerationOutboxPublisher {

    private final GenerationJobRepository jobs;
    private final GenerationMessagePreparation messages;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate writes;
    private final Clock clock;
    private final AtomicBoolean publishing = new AtomicBoolean();
    private final ThreadPoolExecutor wakeups = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
            Thread.ofPlatform().name("generation-outbox-wakeup-", 0).factory(),
            new ThreadPoolExecutor.DiscardPolicy());

    public GenerationOutboxPublisher(GenerationJobRepository jobs, GenerationMessagePreparation messages,
            RabbitTemplate rabbit, PlatformTransactionManager transactionManager, Clock clock) {
        this.jobs = jobs;
        this.messages = messages;
        this.rabbit = rabbit;
        this.rabbit.setMandatory(true);
        this.writes = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 500)
    public void publishNext() {
        if (!publishing.compareAndSet(false, true)) {
            return;
        }
        try {
            publishOne();
        } finally {
            publishing.set(false);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void wakeAfterCommit(GenerationMessagePreparation.PublicationReady event) {
        // 신호가 겹치거나 누락되어도 DB 작업은 다음 폴링에서 처리합니다.
        wakeups.execute(this::publishNext);
    }

    @PreDestroy
    void stopWakeups() {
        wakeups.shutdownNow();
    }

    private void publishOne() {
        Claim claim = writes.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            return jobs.findNextPublicationForUpdate(now).map(job -> {
                String token = UUID.randomUUID().toString();
                job.claimPublication(token, now.plusSeconds(60));
                try {
                    return new Claim(job.getId(), job.getAttemptCount(), token,
                            job.getMessageId(), messages.buildMessage(job));
                } catch (RuntimeException exception) {
                    job.finishPublication(job.getAttemptCount(), token, "MQ_INVALID_SNAPSHOT", now);
                    return null;
                }
            }).orElse(null);
        });
        if (claim == null) {
            return;
        }
        String error = publish(claim);
        writes.executeWithoutResult(status -> jobs.findByIdForUpdate(claim.jobId()).ifPresent(job ->
                job.finishPublication(claim.attempt(), claim.token(), error, LocalDateTime.now(clock))));
    }

    private String publish(Claim claim) {
        var properties = new MessageProperties();
        properties.setMessageId(claim.messageId());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation = new CorrelationData(claim.token());
        try {
            rabbit.send(GenerationRabbitConfiguration.EXCHANGE,
                    GenerationRabbitConfiguration.ROUTING_KEY,
                    new Message(claim.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                return "MQ_UNROUTABLE";
            }
            return confirm.isAck() ? null : "MQ_NACK";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "MQ_INTERRUPTED";
        } catch (java.util.concurrent.TimeoutException exception) {
            return "MQ_CONFIRM_TIMEOUT";
        } catch (java.util.concurrent.ExecutionException | org.springframework.amqp.AmqpException exception) {
            return "MQ_PUBLISH_FAILED";
        }
    }

    private record Claim(Long jobId, int attempt, String token, String messageId, String payload) {
    }
}
