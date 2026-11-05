package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GenerationCallbackControllerTest {

    @Test
    void logsSuccessfullyAppliedCallback() {
        GenerationCallbackTransactionService transactions =
                mock(GenerationCallbackTransactionService.class);
        GenerationCallbackController controller = new GenerationCallbackController(transactions);
        ListAppender<ILoggingEvent> logs = captureLogs();
        GenerationCallbackEnvelope envelope = new GenerationCallbackEnvelope(
                "guidebook_processing",
                new GenerationCallbackEnvelope.Data(
                        UUID.randomUUID(),
                        "301-0",
                        2L,
                        "ai-job-301",
                        AiGenerationStatus.PROCESSING,
                        new GenerationCallbackEnvelope.Progress("arranging", "일정을 구성하고 있어요."),
                        null,
                        null));
        when(transactions.receive(envelope)).thenReturn(
                new GenerationCallbackTransactionService.CallbackHandlingResult(
                        true, GenerationStatus.PROCESSING, null));

        assertThat(controller.receive(envelope).getStatusCode().value()).isEqualTo(204);

        verify(transactions).receive(envelope);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("AI 생성 콜백을 반영했습니다.");
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    "event", "ai_generation_callback_applied"),
                            org.assertj.core.groups.Tuple.tuple("backendJobId", 301L),
                            org.assertj.core.groups.Tuple.tuple("requestId", "301-0"),
                            org.assertj.core.groups.Tuple.tuple("aiJobId", "ai-job-301"),
                            org.assertj.core.groups.Tuple.tuple("sequence", 2L),
                            org.assertj.core.groups.Tuple.tuple(
                                    "receivedStatus", AiGenerationStatus.PROCESSING),
                            org.assertj.core.groups.Tuple.tuple(
                                    "status", GenerationStatus.PROCESSING),
                            org.assertj.core.groups.Tuple.tuple("errorCode", null));
        });
    }

    @Test
    void logsFinalTimeoutStateInsteadOfReceivedCallbackStatus() {
        GenerationCallbackTransactionService transactions =
                mock(GenerationCallbackTransactionService.class);
        GenerationCallbackController controller = new GenerationCallbackController(transactions);
        ListAppender<ILoggingEvent> logs = captureLogs();
        GenerationCallbackEnvelope envelope = new GenerationCallbackEnvelope(
                "guidebook_completed",
                new GenerationCallbackEnvelope.Data(
                        UUID.randomUUID(),
                        "301-0",
                        3L,
                        "ai-job-301",
                        AiGenerationStatus.COMPLETED,
                        null,
                        null,
                        null));
        when(transactions.receive(envelope)).thenReturn(
                new GenerationCallbackTransactionService.CallbackHandlingResult(
                        true, GenerationStatus.FAILED, "GENERATION_TIMEOUT"));

        assertThat(controller.receive(envelope).getStatusCode().value()).isEqualTo(204);

        assertThat(logs.list).singleElement().satisfies(event ->
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key, pair -> pair.value)
                        .contains(
                                org.assertj.core.groups.Tuple.tuple(
                                        "receivedStatus", AiGenerationStatus.COMPLETED),
                                org.assertj.core.groups.Tuple.tuple(
                                        "status", GenerationStatus.FAILED),
                                org.assertj.core.groups.Tuple.tuple(
                                        "errorCode", "GENERATION_TIMEOUT")));
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                GenerationCallbackController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
