package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.ContentType;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Coordinates;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiClientContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesGenerationRequestUsingAiApiFieldNames() throws Exception {
        AiGuidebookRequest request = new AiGuidebookRequest(
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
                List.of(new AiGuidebookRequest.Content(
                        "126508",
                        ContentType.PLACE,
                        "첨성대",
                        "HS",
                        "HS01",
                        "HS010100",
                        "경상북도 경주시 인왕동",
                        new Coordinates(35.8347, 129.219),
                        "https://example.com/126508.jpg",
                        null)));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("contents").get(0).has("category")).isFalse();

        assertThat(json.get("request_id").asText()).isEqualTo("301-0");
        assertThat(json.get("start_date").asText()).isEqualTo("26.10.12");
        assertThat(json.get("end_date").asText()).isEqualTo("26.10.14");
        assertThat(json.get("people_count").asInt()).isEqualTo(2);
        assertThat(json.get("preferences").get("large_category").get(0).asText())
                .isEqualTo("힐링");
        assertThat(json.get("contents").isArray()).isTrue();
        assertThat(json.get("contents").get(0).get("content_type").asText())
                .isEqualTo("PLACE");
        assertThat(json.get("contents").get(0).get("coordinates").get("lat").asDouble())
                .isEqualTo(35.8347);
    }

    @Test
    void deserializesAcceptedResponseEnvelope() throws Exception {
        String json = """
                {
                  "message": "guidebook_accepted",
                  "data": {
                    "job_id": "job_12345",
                    "status": "pending",
                    "remaining_quota": 4
                  }
                }
                """;

        AiGenerationAcceptedEnvelope envelope = objectMapper.readValue(
                json,
                AiGenerationAcceptedEnvelope.class);
        AiGenerationAcceptedResponse response = envelope.data();

        assertThat(envelope.message()).isEqualTo("guidebook_accepted");
        assertThat(response.jobId()).isEqualTo("job_12345");
        assertThat(response.status()).isEqualTo(AiGenerationStatus.PENDING);
    }

    @Test
    void deserializesCompletedStatusEnvelopeUsingCompactDate() throws Exception {
        String json = """
                {
                  "message": "guidebook_completed",
                  "data": {
                    "job_id": "job_12345",
                    "status": "completed",
                    "result": {
                      "title": "경주 역사 여행",
                      "summary": "여행 요약",
                      "itinerary": [{
                        "day": 1,
                        "date": "26.10.12",
                        "places": [{
                          "order": 1,
                          "time": "09:00",
                          "content_id": "126508",
                          "duration_minutes": 60,
                          "description": "장소 설명",
                          "recommend_reason": "추천 이유",
                          "tip": "방문 팁"
                        }]
                      }]
                    },
                    "error": null
                  }
                }
                """;

        AiGenerationStatusEnvelope envelope = objectMapper.readValue(
                json, AiGenerationStatusEnvelope.class);
        AiGenerationStatusResponse response = envelope.data();

        assertThat(response.status()).isEqualTo(AiGenerationStatus.COMPLETED);
        assertThat(response.result().itinerary().get(0).date())
                .isEqualTo(LocalDate.of(2026, 10, 12));
    }
}
