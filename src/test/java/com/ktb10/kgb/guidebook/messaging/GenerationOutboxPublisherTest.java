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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final GenerationOutboxPublisher publisher;

    GenerationOutboxPublisherTest() {
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        publisher = new GenerationOutboxPublisher(
                jobs, messages, rabbit, transactions, CLOCK);
    }

    @AfterEach
    void stopPublisher() {
        publisher.stopWakeups();
    }

    @Test
    void ackCommitsClaimBeforePublishingAndRecordsQueuedInSecondTransaction() {
        GenerationJob job = publishPendingJob();
        when(jobs.findNextPublicationForUpdate(NOW)).thenReturn(Optional.of(job));
        when(jobs.findByIdForUpdate(123L)).thenReturn(Optional.of(job));
        when(messages.buildMessage(job)).thenReturn("{\"messageId\":\"123-0\"}");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(
                eq(""), eq(GenerationRabbitConfiguration.QUEUE), any(Message.class),
                any(CorrelationData.class));

        publisher.publishNext();

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(job.getPublishedAt()).isEqualTo(NOW);
        assertThat(job.getPublishClaimToken()).isNull();
        assertThat(job.getNextPublishAt()).isNull();
        verify(transactions, times(2)).commit(transactionStatus);
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(""), eq(GenerationRabbitConfiguration.QUEUE),
                messageCaptor.capture(), any(CorrelationData.class));
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo("123-0");
        assertThat(messageCaptor.getValue().getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        var order = inOrder(transactions, rabbit);
        order.verify(transactions).commit(transactionStatus);
        order.verify(rabbit).send(eq(""), eq(GenerationRabbitConfiguration.QUEUE),
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
                eq(""), eq(GenerationRabbitConfiguration.QUEUE), any(Message.class),
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
        when(jobs.findNextPublicationForUpdate(NOW)).thenReturn(Optional.of(job));
        when(messages.buildMessage(job)).thenThrow(new IllegalStateException("broken snapshot"));

        publisher.publishNext();

        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PUBLISH_PENDING);
        assertThat(job.getLastPublishErrorCode()).isEqualTo("MQ_INVALID_SNAPSHOT");
        assertThat(job.getNextPublishAt()).isEqualTo(NOW.plusSeconds(2));
        verify(rabbit, never()).send(any(), any(), any(Message.class), any(CorrelationData.class));
        verify(jobs, never()).findByIdForUpdate(any());
        verify(transactions).commit(transactionStatus);
    }

    @Test
    void overlappingWakeupDoesNotPublishTheSameProcessTwice() throws Exception {
        GenerationJob job = publishPendingJob();
        when(jobs.findNextPublicationForUpdate(NOW)).thenReturn(Optional.of(job));
        when(jobs.findByIdForUpdate(123L)).thenReturn(Optional.of(job));
        when(messages.buildMessage(job)).thenReturn("{}");
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(
                eq(""), eq(GenerationRabbitConfiguration.QUEUE), any(Message.class),
                any(CorrelationData.class));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(publisher::publishNext);
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            publisher.publishNext();
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        }

        verify(jobs).findNextPublicationForUpdate(NOW);
        verify(rabbit).send(eq(""), eq(GenerationRabbitConfiguration.QUEUE),
                any(Message.class), any(CorrelationData.class));
    }

    private GenerationJob publishPendingJob() {
        GenerationJob job = GenerationJob.createInitial(
                mock(Member.class), "{}", "key", NOW.minusMinutes(1));
        ReflectionTestUtils.setField(job, "id", 123L);
        job.preparePublication("{}", NOW);
        return job;
    }
}
