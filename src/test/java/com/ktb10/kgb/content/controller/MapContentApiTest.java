package com.ktb10.kgb.content.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import com.ktb10.kgb.content.repository.MapContentQuery;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:map-content-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
class MapContentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapContentQuery mapContentQuery;

    @Test
    void returnsPlacesAndEventsWithinBounds() throws Exception {
        when(mapContentQuery.findWithinBounds(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        new MapContentItemResponse(
                                "126508",
                                "첨성대",
                                ContentType.PLACE,
                                "경상북도 경주시 인왕동",
                                35.8347,
                                129.219,
                                null,
                                null),
                        new MapContentItemResponse(
                                "event-1",
                                "가을 축제",
                                ContentType.EVENT,
                                "경기도 성남시",
                                37.395,
                                127.11,
                                "https://example.com/event.jpg",
                                new EventPeriod(
                                        LocalDate.of(2026, 10, 1),
                                        LocalDate.of(2026, 10, 3)))));

        mockMvc.perform(get("/api/v1/map/contents")
                        .param("south", "35.80")
                        .param("west", "129.18")
                        .param("north", "35.90")
                        .param("east", "129.28")
                        .param("zoom", "16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("map_content_success"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].content_id").value("126508"))
                .andExpect(jsonPath("$.data.items[0].content_type").value("PLACE"))
                .andExpect(jsonPath("$.data.items[0].event_period").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].content_type").value("EVENT"))
                .andExpect(jsonPath("$.data.items[1].event_period.start_date")
                        .value("2026-10-01"))
                .andExpect(jsonPath("$.data.has_more").value(false))
                .andExpect(jsonPath("$.data.clusters").doesNotExist());
    }

    @Test
    void rejectsMissingBoundsParameter() throws Exception {
        mockMvc.perform(get("/api/v1/map/contents")
                        .param("south", "37.35")
                        .param("west", "127.05")
                        .param("north", "37.45")
                        .param("zoom", "16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidZoomAndLimit() throws Exception {
        mockMvc.perform(get("/api/v1/map/contents")
                        .param("south", "37.35")
                        .param("west", "127.05")
                        .param("north", "37.45")
                        .param("east", "127.15")
                        .param("zoom", "22")
                        .param("limit", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void rejectsTooLargeBounds() throws Exception {
        mockMvc.perform(get("/api/v1/map/contents")
                        .param("south", "37.0")
                        .param("west", "127.0")
                        .param("north", "37.3")
                        .param("east", "127.3")
                        .param("zoom", "16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("bounds"))
                .andExpect(jsonPath("$.error.details[0].reason").value("area_too_large"));
    }
}

