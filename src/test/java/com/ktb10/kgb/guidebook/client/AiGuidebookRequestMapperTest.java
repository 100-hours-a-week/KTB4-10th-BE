package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Content;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.ContentType;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest.Coordinates;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.entity.Companion;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiGuidebookRequestMapperTest {

    private final AiGuidebookContentQuery contentQuery = mock(AiGuidebookContentQuery.class);
    private final AiGuidebookRequestMapper mapper =
            new AiGuidebookRequestMapper(contentQuery);

    @Test
    void mapsStoredInputToAiGenerationRequest() {
        InitialGenerationRequestPayload payload = new InitialGenerationRequestPayload(
                new GuidebookGenerationRequest(
                        "경상북도",
                        "경주시",
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 8, 25),
                        Companion.COUPLE,
                        2),
                List.of(
                        new PreferenceSnapshot("THEME", "NATURE"),
                        new PreferenceSnapshot("DETAIL", "NATURE_MOUNTAIN"),
                        new PreferenceSnapshot("DETAIL", "NATURE_PARK"),
                        new PreferenceSnapshot("TRAVEL_STYLE", "RELAXING")));
        Content content = new Content(
                "1001",
                ContentType.PLACE,
                "불국사",
                "HS",
                "HS01",
                "HS010100",
                "경상북도 경주시",
                new Coordinates(35.7, 129.3),
                null,
                null);
        given(contentQuery.findAll(
                "경상북도",
                "경주시",
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 25)))
                .willReturn(List.of(content));

        var request = mapper.map(payload, 301L, 0);

        assertThat(request.requestId()).isEqualTo("301-0");
        assertThat(request.region().province()).isEqualTo("경상북도");
        assertThat(request.region().city()).isEqualTo("경주시");
        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(request.companion()).isEqualTo("couple");
        assertThat(request.peopleCount()).isEqualTo(2);
        assertThat(request.preferences().largeCategory()).containsExactly("자연");
        assertThat(request.preferences().midCategory())
                .containsEntry("자연", List.of("산", "공원"));
        assertThat(request.preferences().travelStyle()).containsExactly("여유롭게");
        assertThat(request.contents()).containsExactly(content);
    }

    @Test
    void rejectsPreferenceTypeThatDoesNotMatchCode() {
        InitialGenerationRequestPayload payload = new InitialGenerationRequestPayload(
                new GuidebookGenerationRequest(
                        "경상북도",
                        "경주시",
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 8, 25),
                        Companion.COUPLE,
                        2),
                List.of(new PreferenceSnapshot("THEME", "NATURE_MOUNTAIN")));

        assertThatThrownBy(() -> mapper.map(payload, 301L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NATURE_MOUNTAIN");
    }
}
