package com.ktb10.kgb.guidebook.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class GenerationOutboxPublisherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationMessagePreparation messages = mock(GenerationMessagePreparation.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final GenerationOutboxPublisher publisher;

    GenerationOutboxPublisherTest() {
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        publisher = new GenerationOutboxPublisher(
                jobs, messages, rabbit, transactions, CLOCK, events);
    }

    @Test
    void ackCommitsClaimBeforePublishingAndRecordsQueuedInSecondTransaction() {
        GenerationJob job = publishPendingJob();
        when(jobs.findNextPublicationForUpdate(NOW))
                .thenReturn(Optional.of(job), Optional.empty());
        when(jobs.findByIdForUpdate(123L)).thenReturn(Optional.of(job));
        when(messages.buildMessage(job)).thenReturn("{\"messageId\":\"123-0\"}");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(
                eq(GenerationRabbitConfiguration.EXCHANGE),
                eq(GenerationRabbitConfiguration.ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        publisher.publishNext();

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(job.getPublishedAt()).isEqualTo(NOW);
        assertThat(job.getPublishClaimToken()).isNull();
        assertThat(job.getNextPublishAt()).isNull();
        verify(transactions, times(3)).commit(transactionStatus);
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(GenerationRabbitConfiguration.EXCHANGE),
                eq(GenerationRabbitConfiguration.ROUTING_KEY),
                messageCaptor.capture(), any(CorrelationData.class));
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo("123-0");
        assertThat(messageCaptor.getValue().getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        var order = inOrder(transactions, rabbit);
        order.verify(transactions).commit(transactionStatus);
        order.verify(rabbit).send(eq(GenerationRabbitConfiguration.EXCHANGE),
                eq(GenerationRabbitConfiguration.ROUTING_KEY),
                any(Message.class), any(CorrelationData.class));
        order.verify(transactions).commit(transactionStatus);
    }

    @Test
    void brokerFailureKeepsPublishPendingAndSchedulesRetry() {
        GenerationJob job = publishPendingJob();
        when(jobs.findNextPublicationForUpdate(NOW)).thenReturn(Optional.of(job));
        when(jobs.findByIdForUpdate(123L)).thenReturn(Optional.of(job));
        when(messages.buildMessage(job)).thenReturn("{}");
        doThrow(new AmqpException("broker unavailable")).when(rabbit).send(
                eq(GenerationRabbitConfiguration.EXCHANGE),
                eq(GenerationRabbitConfiguration.ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        publisher.publishNext();

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PUBLISH_PENDING);
        assertThat(job.getLastPublishErrorCode()).isEqualTo("MQ_PUBLISH_FAILED");
        assertThat(job.getNextPublishAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(job.getPublishClaimToken()).isNull();
        verify(transactions, times(2)).commit(transactionStatus);
    }

    @Test
    void invalidStoredSnapshotIsRecordedInsideClaimTransactionWithoutCallingBroker() {
        GenerationJob job = publishPendingJob();
        when(jobs.findNextPublicationForUpdate(NOW))
                .thenReturn(Optional.of(job), Optional.empty());
        when(messages.buildMessage(job)).thenThrow(new IllegalStateException("broken snapshot"));

        publisher.publishNext();

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.getLastPublishErrorCode()).isEqualTo("MQ_INVALID_SNAPSHOT");
        assertThat(job.getNextPublishAt()).isNull();
        assertThat(job.getErrorPayload()).contains("GENERATION_REQUEST_INVALID");
        verify(events).publishEvent(any(com.ktb10.kgb.guidebook.callback.GenerationUpdate.class));
        verify(rabbit, never()).send(any(), any(), any(Message.class), any(CorrelationData.class));
        verify(jobs, never()).findByIdForUpdate(any());
        verify(transactions, times(2)).commit(transactionStatus);
    }

    @Test
    void onePollDrainsMultiplePendingJobsAsIndividualMessages() {
        GenerationJob first = publishPendingJob(123L);
        GenerationJob second = publishPendingJob(124L);
        GenerationJob third = publishPendingJob(125L);
        when(jobs.findNextPublicationForUpdate(NOW)).thenReturn(
                Optional.of(first), Optional.of(second), Optional.of(third), Optional.empty());
        when(jobs.findByIdForUpdate(123L)).thenReturn(Optional.of(first));
        when(jobs.findByIdForUpdate(124L)).thenReturn(Optional.of(second));
        when(jobs.findByIdForUpdate(125L)).thenReturn(Optional.of(third));
        when(messages.buildMessage(any(GenerationJob.class))).thenReturn("{}");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(GenerationRabbitConfiguration.EXCHANGE),
                eq(GenerationRabbitConfiguration.ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        publisher.publishNext();

        assertThat(first.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(second.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(third.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        verify(rabbit, times(3)).send(eq(GenerationRabbitConfiguration.EXCHANGE),
                eq(GenerationRabbitConfiguration.ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));
    }

    private GenerationJob publishPendingJob() {
        return publishPendingJob(123L);
    }

    private GenerationJob publishPendingJob(Long id) {
        GenerationJob job = GenerationJob.createInitial(
                mock(Member.class), "{}", "key", NOW.minusMinutes(1));
        ReflectionTestUtils.setField(job, "id", id);
        job.preparePublication("{}", NOW);
        return job;
    }
}
