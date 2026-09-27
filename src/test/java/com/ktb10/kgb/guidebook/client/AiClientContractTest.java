package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedEnvelope;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationAcceptedResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiClientContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesGenerationRequestUsingAiApiFieldNames() throws Exception {
        AiGuidebookRequest request = new AiGuidebookRequest(
                new AiGuidebookRequest.Region("경상북도", "경주시"),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                "couple",
                2,
                new AiGuidebookRequest.Preferences(
                        List.of("힐링"),
                        Map.of("힐링", List.of("조용한 곳")),
                        List.of("여유롭게")));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("start_date").asText()).isEqualTo("26.10.12");
        assertThat(json.get("end_date").asText()).isEqualTo("26.10.14");
        assertThat(json.get("people_count").asInt()).isEqualTo(2);
        assertThat(json.get("preferences").get("large_category").get(0).asText())
                .isEqualTo("힐링");
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
}
