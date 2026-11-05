package com.ktb10.kgb.guidebook.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class GenerationUpdateRelayTest {

    @Test
    void logsSuccessfullyRelayedLocalSseUpdate() {
        GenerationStreamService streams = mock(GenerationStreamService.class);
        GenerationTimeoutRegistry timers = mock(GenerationTimeoutRegistry.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        GenerationUpdateRelay relay = new GenerationUpdateRelay(
                streams, timers, redis, new ObjectMapper(), false, "generation-test");
        GenerationUpdate update = new GenerationUpdate(
                7L, 301L, "301-0", 4L, GenerationStatus.COMPLETED,
                null, 901L, null);
        ListAppender<ILoggingEvent> logs = captureLogs();

        relay.relay(update);

        verify(timers).cancel("301-0");
        verify(streams).publish(update);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("생성 상태 실시간 전달을 처리했습니다.");
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    "event", "generation_update_relayed"),
                            org.assertj.core.groups.Tuple.tuple("backendJobId", 301L),
                            org.assertj.core.groups.Tuple.tuple("requestId", "301-0"),
                            org.assertj.core.groups.Tuple.tuple("sequence", 4L),
                            org.assertj.core.groups.Tuple.tuple(
                                    "status", GenerationStatus.COMPLETED),
                            org.assertj.core.groups.Tuple.tuple("guidebookId", 901L),
                            org.assertj.core.groups.Tuple.tuple("deliveryMode", "local_sse"));
        });
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                GenerationUpdateRelay.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
