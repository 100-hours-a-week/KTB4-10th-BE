package com.ktb10.kgb.content.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.ktb10.kgb.content.dto.MapClusterResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.dto.MapContentItemResponse.EventPeriod;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.content.repository.MapContentQuery;
import com.ktb10.kgb.content.repository.MapClusterQueryResult;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import com.ktb10.kgb.content.repository.MapContentPersonalization;
import com.ktb10.kgb.content.repository.MapContentPersonalizationQuery;
import com.ktb10.kgb.content.service.MapContentCommonCache;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:map-content-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class MapContentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapContentQuery mapContentQuery;

    @MockitoBean
    private MapContentPersonalizationQuery personalizationQuery;

    @MockitoBean
    private MapContentCommonCache commonCache;

    @BeforeEach
    void setUpPersonalization() {
        when(personalizationQuery.findByMemberAndContentIds(anyLong(), anyList()))
                .thenReturn(MapContentPersonalization.empty());
    }

    @Test
    void returnsPlacesAndEventsWithinBounds() throws Exception {
        when(commonCache.get(
                anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(
                        new MapContentCommonData(
                                101L,
                                "126508",
                                "첨성대",
                                ContentType.PLACE,
                                "경상북도 경주시 인왕동",
                                35.8347,
                                129.219,
                                null,
                                null),
                        new MapContentCommonData(
                                102L,
                                "event-1",
                                "가을 축제",
                                ContentType.EVENT,
                                "경상북도 경주시",
                                35.85,
                                129.22,
                                "https://example.com/event.jpg",
                                new EventPeriod(
                                        LocalDate.of(2026, 10, 1),
                                        LocalDate.of(2026, 10, 3)))));
        when(personalizationQuery.findByMemberAndContentIds(1L, List.of(101L, 102L)))
                .thenReturn(new MapContentPersonalization(
                        java.util.Set.of(101L),
                        java.util.Set.of(101L)));

        mockMvc.perform(get("/api/v1/map/contents")
                        .with(authentication(authenticatedMember()))
                        .param("south", "35.80")
                        .param("west", "129.18")
                        .param("north", "35.90")
                        .param("east", "129.28")
                        .param("zoom", "16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("map_content_success"))
                .andExpect(jsonPath("$.data.mode").value("CONTENT"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].content_id").value("126508"))
                .andExpect(jsonPath("$.data.items[0].content_type").value("PLACE"))
                .andExpect(jsonPath("$.data.items[0].is_favorite").value(true))
                .andExpect(jsonPath("$.data.items[0].is_in_guidebook").value(true))
                .andExpect(jsonPath("$.data.items[0].event_period").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].content_type").value("EVENT"))
                .andExpect(jsonPath("$.data.items[1].event_period.start_date")
                        .value("2026-10-01"))
                .andExpect(jsonPath("$.data.has_more").value(false))
                .andExpect(jsonPath("$.data.clusters").isEmpty());
    }

    @Test
    void returnsServerClustersAndRepresentativesForWideZoom() throws Exception {
        when(mapContentQuery.findClustersWithinBounds(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new MapClusterQueryResult(
                        new MapClusterResponse("37:126", 37.5, 126.9, 120),
                        new MapContentCommonData(
                                201L,
                                "representative-1",
                                "광역 대표 장소",
                                ContentType.PLACE,
                                "서울",
                                37.5,
                                126.9,
                                null,
                                null))));
        when(personalizationQuery.findByMemberAndContentIds(1L, List.of(201L)))
                .thenReturn(MapContentPersonalization.empty());

        mockMvc.perform(get("/api/v1/map/contents")
                        .with(authentication(authenticatedMember()))
                        .param("south", "33.0")
                        .param("west", "124.0")
                        .param("north", "39.0")
                        .param("east", "132.0")
                        .param("zoom", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("CLUSTER"))
                .andExpect(jsonPath("$.data.clusters.length()").value(1))
                .andExpect(jsonPath("$.data.clusters[0].count").value(120))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content_id")
                        .value("representative-1"))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void rejectsMissingBoundsParameter() throws Exception {
        mockMvc.perform(get("/api/v1/map/contents")
                        .with(authentication(authenticatedMember()))
                        .param("south", "37.35")
                        .param("west", "127.05")
                        .param("north", "37.45")
                        .param("zoom", "16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void acceptsClientZoomAndIgnoresLegacyLimit() throws Exception {
        mockMvc.perform(get("/api/v1/map/contents")
                        .with(authentication(authenticatedMember()))
                        .param("south", "37.35")
                        .param("west", "127.05")
                        .param("north", "37.45")
                        .param("east", "127.15")
                        .param("zoom", "22")
                        .param("limit", "201"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsLargeValidBounds() throws Exception {
        mockMvc.perform(get("/api/v1/map/contents")
                        .with(authentication(authenticatedMember()))
                        .param("south", "37.0")
                        .param("west", "127.0")
                        .param("north", "37.3")
                        .param("east", "127.3")
                        .param("zoom", "16"))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken authenticatedMember() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(1L, MemberStatus.ACTIVE, 10L),
                null,
                List.of());
    }
}
