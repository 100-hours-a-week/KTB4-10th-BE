package com.ktb10.kgb.guidebook.messaging;

import com.ktb10.kgb.guidebook.callback.GenerationUpdate;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "ai.generation.outbox-enabled", havingValue = "true")
public class GenerationOutboxPublisher {

    private static final int MAX_DRAIN_COUNT = 50;
    private static final long MAX_DRAIN_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final String INVALID_SNAPSHOT_ERROR = "MQ_INVALID_SNAPSHOT";
    private static final String INVALID_SNAPSHOT_FAILURE_PAYLOAD =
            "{\"code\":\"GENERATION_REQUEST_INVALID\","
                    + "\"message\":\"생성 요청 데이터를 처리할 수 없습니다.\",\"retryable\":false}";

    private final GenerationJobRepository jobs;
    private final GenerationMessagePreparation messages;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate writes;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean publishing = new AtomicBoolean();
    private final ThreadPoolExecutor wakeups = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
            Thread.ofPlatform().name("generation-outbox-wakeup-", 0).factory(),
            new ThreadPoolExecutor.DiscardPolicy());

    public GenerationOutboxPublisher(GenerationJobRepository jobs, GenerationMessagePreparation messages,
            RabbitTemplate rabbit, PlatformTransactionManager transactionManager, Clock clock,
            ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.messages = messages;
        this.rabbit = rabbit;
        this.rabbit.setMandatory(true);
        this.writes = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.events = events;
    }

    @Scheduled(fixedDelay = 500)
    public void publishNext() {
        if (!publishing.compareAndSet(false, true)) {
            return;
        }
        boolean shouldContinue;
        try {
            shouldContinue = drainAvailablePublications();
        } finally {
            publishing.set(false);
        }
        if (shouldContinue) {
            scheduleWakeup();
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void wakeAfterCommit(GenerationMessagePreparation.PublicationReady event) {
        // 신호가 겹치거나 누락되어도 DB 작업은 다음 폴링에서 처리합니다.
        scheduleWakeup();
    }

    @PreDestroy
    void stopWakeups() {
        wakeups.shutdownNow();
    }

    private void scheduleWakeup() {
        wakeups.execute(this::publishNext);
    }

    private boolean drainAvailablePublications() {
        long deadline = System.nanoTime() + MAX_DRAIN_NANOS;
        for (int published = 0; published < MAX_DRAIN_COUNT
                && System.nanoTime() < deadline; published++) {
            PublishOutcome outcome = publishOne();
            if (outcome == PublishOutcome.EMPTY || outcome == PublishOutcome.BROKER_FAILURE) {
                return false;
            }
        }
        return true;
    }

    private PublishOutcome publishOne() {
        Selection selection = writes.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            return jobs.findNextPublicationForUpdate(now).map(job -> {
                String token = UUID.randomUUID().toString();
                job.claimPublication(token, now.plusSeconds(60));
                try {
                    Claim claim = new Claim(job.getId(), job.getAttemptCount(), token,
                            job.getMessageId(), messages.buildMessage(job));
                    return Selection.claimed(claim);
                } catch (RuntimeException exception) {
                    job.failPublication(job.getAttemptCount(), token, INVALID_SNAPSHOT_ERROR,
                            INVALID_SNAPSHOT_FAILURE_PAYLOAD, now);
                    events.publishEvent(GenerationUpdate.from(
                            job, 0, null, "GENERATION_REQUEST_INVALID"));
                    return Selection.invalid();
                }
            }).orElseGet(Selection::empty);
        });
        if (selection == null || selection.outcome() != PublishOutcome.PUBLISHED) {
            return selection == null ? PublishOutcome.EMPTY : selection.outcome();
        }
        Claim claim = selection.claim();
        String error = publish(claim);
        writes.executeWithoutResult(status -> jobs.findByIdForUpdate(claim.jobId()).ifPresent(job ->
                job.finishPublication(claim.attempt(), claim.token(), error, LocalDateTime.now(clock))));
        return error == null ? PublishOutcome.PUBLISHED : PublishOutcome.BROKER_FAILURE;
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

    private record Selection(Claim claim, PublishOutcome outcome) {

        private static Selection claimed(Claim claim) {
            return new Selection(claim, PublishOutcome.PUBLISHED);
        }

        private static Selection invalid() {
            return new Selection(null, PublishOutcome.INVALID_SNAPSHOT);
        }

        private static Selection empty() {
            return new Selection(null, PublishOutcome.EMPTY);
        }
    }

    private enum PublishOutcome {
        PUBLISHED,
        INVALID_SNAPSHOT,
        BROKER_FAILURE,
        EMPTY
    }
}
