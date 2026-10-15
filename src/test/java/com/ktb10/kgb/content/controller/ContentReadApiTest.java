package com.ktb10.kgb.content.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.content.dto.ContentListResponse;
import com.ktb10.kgb.content.dto.ContentListResponse.ContentEventPeriodResponse;
import com.ktb10.kgb.content.dto.ContentListResponse.ContentListItemResponse;
import com.ktb10.kgb.content.dto.ContentListResponse.ContentRegionResponse;
import com.ktb10.kgb.content.service.ContentReadService;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content-read-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ContentReadApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentReadService contentReadService;

    @Test
    void returnsEventList() throws Exception {
        when(contentReadService.getEvents(null, 1, 20)).thenReturn(new ContentListResponse(
                List.of(new ContentListItemResponse(
                        "event-1", "수원 야행", "EVENT",
                        new ContentRegionResponse("41", "경기도"), "수원시", null,
                        new ContentEventPeriodResponse(
                                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 30)))),
                1, 20, 1, 1));

        mockMvc.perform(get("/api/v1/contents")
                        .with(authentication(authenticatedMember()))
                        .param("category", "EVENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("content_list_success"))
                .andExpect(jsonPath("$.data.items[0].content_id").value("event-1"))
                .andExpect(jsonPath("$.data.items[0].event_period.start_date")
                        .value("2026-08-14"));
    }

    @Test
    void rejectsInvalidListParameters() throws Exception {
        mockMvc.perform(get("/api/v1/contents")
                        .with(authentication(authenticatedMember()))
                        .param("category", "PLACE")
                        .param("page", "0")
                        .param("size", "21"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/contents"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticatedMember() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(1L, MemberStatus.ACTIVE, 10L),
                null,
                List.of());
    }
}
