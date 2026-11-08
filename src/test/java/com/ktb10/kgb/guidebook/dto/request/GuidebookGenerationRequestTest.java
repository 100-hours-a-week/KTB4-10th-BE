package com.ktb10.kgb.guidebook.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.guidebook.entity.Companion;
import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuidebookGenerationRequestTest {

    private GuidebookGenerationRequest request(String text) {
        return new GuidebookGenerationRequest("서울특별시", "중구", LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 22), Companion.COUPLE, 2, text);
    }

    @Test
    void validatesOptionalRequestLikeRegenerationAfterTrimming() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request(null))).isEmpty();
            for (String text : List.of("", "  ", "\n\t", "가".repeat(201))) {
                assertThat(validator.validate(request(text))).isNotEmpty();
                assertThat(validator.validate(new GuidebookRegenerationRequest(text))).isNotEmpty();
            }
            assertThat(validator.validate(request("  " + "가".repeat(200) + "  "))).isEmpty();
            assertThat(request(" 오후 시작 ")).isEqualTo(request("오후 시작"));
            assertThat(request("오후 시작")).isNotEqualTo(request("오전 시작"));
        }
    }

    @Test
    void persistsRequestAndReadsLegacySnapshotWithoutOptionalField() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var payload = new InitialGenerationRequestPayload(request(" 오후 시작 "), List.of());
        String stored = json.writeValueAsString(payload);
        assertThat(json.readTree(stored).path("request").path("user_request").asText()).isEqualTo("오후 시작");
        assertThat(json.readValue(stored, InitialGenerationRequestPayload.class)).isEqualTo(payload);
        var legacy = json.valueToTree(payload);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.path("request")).remove("user_request");
        assertThat(json.treeToValue(legacy, InitialGenerationRequestPayload.class).request().userRequest()).isNull();
    }
}
