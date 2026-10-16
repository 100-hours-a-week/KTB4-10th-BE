package com.ktb10.kgb.content.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.content.dto.NewEventItemResponse;
import com.ktb10.kgb.content.dto.NewEventListResponse;
import com.ktb10.kgb.content.service.NewEventService;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:new-event-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class NewEventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewEventService newEventService;

    @Test
    void returnsDailyNewEvents() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 14);
        when(newEventService.getNewEvents(date, 1, 20)).thenReturn(
                new NewEventListResponse(
                        date,
                        List.of(new NewEventItemResponse(
                                "event-1",
                                "가을 축제",
                                "서울특별시 중구",
                                "https://example.com/event.jpg",
                                LocalDate.of(2026, 10, 20),
                                LocalDate.of(2026, 10, 21),
                                LocalDateTime.of(2026, 10, 14, 8, 30))),
                        1,
                        20,
                        1,
                        1));

        mockMvc.perform(get("/api/v1/events/new")
                        .with(authentication(authenticatedMember()))
                        .param("date", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("new_event_list_success"))
                .andExpect(jsonPath("$.data.date").value("2026-10-14"))
                .andExpect(jsonPath("$.data.items[0].content_id").value("event-1"))
                .andExpect(jsonPath("$.data.total_items").value(1));
        verify(newEventService).getNewEvents(date, 1, 20);
    }

    @Test
    void rejectsInvalidPageSizeAndDate() throws Exception {
        mockMvc.perform(get("/api/v1/events/new")
                        .with(authentication(authenticatedMember()))
                        .param("date", "2026-13-40")
                        .param("page", "0")
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/events/new"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticatedMember() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(1L, MemberStatus.ACTIVE, 10L),
                null,
                List.of());
    }
}
