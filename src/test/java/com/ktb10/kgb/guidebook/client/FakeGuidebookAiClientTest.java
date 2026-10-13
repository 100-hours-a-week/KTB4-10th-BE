package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    void createsDifferentAiJobIdsAcrossClientInstances() {
        var firstClient = new FakeGuidebookAiClient();
        var restartedClient = new FakeGuidebookAiClient();

        var first = firstClient.requestGeneration(request());
        var afterRestart = restartedClient.requestGeneration(request());

        assertThat(first.jobId()).startsWith("fake-job-");
        assertThat(afterRestart.jobId()).startsWith("fake-job-");
        assertThat(afterRestart.jobId()).isNotEqualTo(first.jobId());
    }

    @Test
    void createsUniqueAiJobIdsForConcurrentRequests() {
        int requestCount = 500;

        Set<String> jobIds = IntStream.range(0, requestCount)
                .parallel()
                .mapToObj(ignored -> client.requestGeneration(request()).jobId())
                .collect(Collectors.toSet());

        assertThat(jobIds).hasSize(requestCount);
    }

    @Test
    void cannotFindInMemoryJobAfterClientRestart() {
        var accepted = client.requestGeneration(request());
        var restartedClient = new FakeGuidebookAiClient();

        assertThatThrownBy(() -> restartedClient.getGenerationStatus(accepted.jobId()))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining(accepted.jobId());
    }

    @Test
    void rejectsCompletionWhenTourismContentsAreEmpty() {
        var accepted = client.requestGeneration(request(List.of()));
        client.getGenerationStatus(accepted.jobId());

        assertThatThrownBy(() -> client.getGenerationStatus(accepted.jobId()))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("관광 콘텐츠가 없습니다");
    }

    @Test
    void rejectsUnknownAiJobId() {
        assertThatThrownBy(() -> client.getGenerationStatus("unknown-job"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("unknown-job");
    }

    private AiGuidebookRequest request() {
        return request(List.of(new AiGuidebookRequest.Content(
                "126508",
                AiGuidebookRequest.ContentType.PLACE,
                "첨성대",
                "HS",
                "HS01",
                "HS010100",
                "경상북도 경주시 인왕동",
                new AiGuidebookRequest.Coordinates(35.8347, 129.219),
                null,
                null)));
    }

    private AiGuidebookRequest request(List<AiGuidebookRequest.Content> contents) {
        return new AiGuidebookRequest(
                "301-0",
                new AiGuidebookRequest.Region("경상북도", "경주시"),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                "couple",
                2,
                new AiGuidebookRequest.Preferences(
                        List.of("힐링"),
                        Map.of("힐링", List.of("조용한 곳")),
                        List.of("여유롭게")),
                contents);
    }
}
