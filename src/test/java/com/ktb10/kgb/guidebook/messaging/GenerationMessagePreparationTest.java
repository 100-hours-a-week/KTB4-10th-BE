package com.ktb10.kgb.guidebook.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.member.entity.Member;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GenerationMessagePreparationTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AiGuidebookRequestMapper mapper = mock(AiGuidebookRequestMapper.class);
    private final Clock clock = Clock.systemUTC();

    @Test
    void regenerationRetryKeepsOriginalInputButUsesNewAttemptIdentity() throws Exception {
        GenerationJob job = GenerationJob.createRegeneration(mock(Member.class), 50L,
                "{}", "key", LocalDateTime.now(clock));
        ReflectionTestUtils.setField(job, "id", 124L);
        String snapshot = "{\"schemaVersion\":1,\"input\":{\"feedback\":\"원본 피드백\"}}";
        job.preparePublication(snapshot, LocalDateTime.now(clock));
        job.fail("{\"code\":\"timeout\"}", LocalDateTime.now(clock));
        job.retry(LocalDateTime.now(clock));
        var preparation = new GenerationMessagePreparation(mapper, json, clock, mock(RegenerationInputSnapshot.class));
        preparation.prepareRegenerationRetry(job, snapshot);
        assertThat(job.getMessagePayload()).isEqualTo(snapshot);
        var message = json.readTree(preparation.buildMessage(job));
        assertThat(message.path("messageId").asText()).isEqualTo("124-1");
        assertThat(message.path("jobType").asText()).isEqualTo("REGENERATION");
        assertThat(message.path("input").path("feedback").asText()).isEqualTo("원본 피드백");
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void storesCompleteInputAndKeepsItImmutableAcrossPublicationRetries() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 10);
        var input = new InitialGenerationRequestPayload(
                new GuidebookGenerationRequest("서울특별시", "중구", date, date, Companion.COUPLE, 2),
                List.of());
        GenerationJob job = GenerationJob.createInitial(
                mock(Member.class), json.writeValueAsString(input), "key", LocalDateTime.now(clock));
        var content = new AiGuidebookRequest.Content("100", AiGuidebookRequest.ContentType.PLACE,
                "장소", "원본 설명", "HS", "HS01", null, "서울", null, null, null);
        var request = new AiGuidebookRequest("123-0", new AiGuidebookRequest.Region("서울특별시", "중구"),
                date, date, "couple", 2,
                new AiGuidebookRequest.Preferences(List.of(), Map.of(), List.of()), List.of(content));
        when(mapper.mapInput(any())).thenReturn(request);
        var preparation = new GenerationMessagePreparation(mapper, json, clock, mock(RegenerationInputSnapshot.class));

        preparation.prepareInitial(job);

        var message = json.readTree(job.getMessagePayload());
        assertThat(job.getId()).isNull();
        assertThat(message.has("messageId")).isFalse();
        assertThat(message.has("jobId")).isFalse();
        assertThat(message.path("input").has("request_id")).isFalse();
        assertThat(message.path("input").path("contents").get(0).path("overview").asText())
                .isEqualTo("원본 설명");
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PUBLISH_PENDING);
        assertThat(job.getNextPublishAt()).isNotNull();
        String snapshot = job.getMessagePayload();
        ReflectionTestUtils.setField(job, "id", 123L);
        String published = preparation.buildMessage(job);
        var envelope = json.readTree(published);
        assertThat(envelope.path("messageId").asText()).isEqualTo("123-0");
        assertThat(envelope.path("jobId").asLong()).isEqualTo(123L);
        assertThat(envelope.path("input").path("request_id").asText()).isEqualTo("123-0");
        assertThat(preparation.buildMessage(job)).isEqualTo(published);
        org.mockito.Mockito.verify(mapper).mapInput(any());
        assertThat(job.getMessagePayload()).isEqualTo(snapshot);
        assertThatThrownBy(() -> preparation.prepareInitial(job)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getMessagePayload()).isEqualTo(snapshot);
    }

    @Test
    void brokenStoredInputDoesNotMoveJobToPublishPending() {
        GenerationJob job = GenerationJob.createInitial(mock(Member.class), "invalid", "key",
                LocalDateTime.now(clock));
        ReflectionTestUtils.setField(job, "id", 123L);
        var preparation = new GenerationMessagePreparation(mapper, json, clock, mock(RegenerationInputSnapshot.class));
        assertThatThrownBy(() -> preparation.prepareInitial(job)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.getMessagePayload()).isNull();
    }
}
