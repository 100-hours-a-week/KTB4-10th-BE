package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FakeGuidebookAiClientTest {

    private final FakeGuidebookAiClient client = new FakeGuidebookAiClient();

    @Test
    void returnsProcessingThenCompletedResult() {
        var accepted = client.requestGeneration(request());

        var processing = client.getGenerationStatus(accepted.jobId());
        var completed = client.getGenerationStatus(accepted.jobId());

        assertThat(accepted.status()).isEqualTo(AiGenerationStatus.PENDING);
        assertThat(processing.status()).isEqualTo(AiGenerationStatus.PROCESSING);
        assertThat(processing.result()).isNull();
        assertThat(completed.status()).isEqualTo(AiGenerationStatus.COMPLETED);
        assertThat(completed.result().itinerary()).hasSize(3);
        assertThat(completed.result().itinerary())
                .extracting(day -> day.day())
                .containsExactly(1, 2, 3);
    }

    @Test
    void createsDifferentAiJobIds() {
        var first = client.requestGeneration(request());
        var second = client.requestGeneration(request());

        assertThat(first.jobId()).isNotEqualTo(second.jobId());
    }

    @Test
    void resetsStatusFlowWhenRetrying() {
        var accepted = client.requestGeneration(request());
        client.getGenerationStatus(accepted.jobId());
        client.getGenerationStatus(accepted.jobId());

        var retried = client.retryGeneration(accepted.jobId());
        var processing = client.getGenerationStatus(accepted.jobId());

        assertThat(retried.status()).isEqualTo(AiGenerationStatus.PENDING);
        assertThat(processing.status()).isEqualTo(AiGenerationStatus.PROCESSING);
    }

    @Test
    void rejectsUnknownAiJobId() {
        assertThatThrownBy(() -> client.getGenerationStatus("unknown-job"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("unknown-job");
    }

    private AiGuidebookRequest request() {
        return new AiGuidebookRequest(
                new AiGuidebookRequest.Region("경상북도", "경주시"),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                "couple",
                2,
                new AiGuidebookRequest.Preferences(
                        List.of("힐링"),
                        Map.of("힐링", List.of("조용한 곳")),
                        List.of("여유롭게")));
    }
}
