package com.ktb10.kgb.content.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.content.dto.FavoriteContentListResponse;
import com.ktb10.kgb.content.dto.FavoriteContentListResponse.FavoriteContentListItemResponse;
import com.ktb10.kgb.content.dto.FavoriteContentResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.service.FavoriteContentService;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:favorite-content-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class FavoriteContentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteContentService favoriteContentService;

    @Test
    void returnsFavoriteList() throws Exception {
        when(favoriteContentService.getFavorites(1L, null, 20))
                .thenReturn(new FavoriteContentListResponse(
                        List.of(new FavoriteContentListItemResponse(
                                "126508",
                                "첨성대",
                                ContentType.PLACE,
                                "경상북도 경주시",
                                null,
                                null,
                                true)),
                        null,
                        false));

        mockMvc.perform(get("/api/v1/members/me/favorites")
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("favorite_list_success"))
                .andExpect(jsonPath("$.data.items[0].content_id").value("126508"))
                .andExpect(jsonPath("$.data.items[0].content_type").value("PLACE"))
                .andExpect(jsonPath("$.data.items[0].event_period").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].is_favorite").value(true))
                .andExpect(jsonPath("$.data.next_cursor").value((Object) null))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void rejectsInvalidFavoriteListSize() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/favorites")
                        .param("size", "21")
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void returnsEmptyFavoriteList() throws Exception {
        when(favoriteContentService.getFavorites(1L, null, 20))
                .thenReturn(new FavoriteContentListResponse(List.of(), null, false));

        mockMvc.perform(get("/api/v1/members/me/favorites")
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").value((Object) null))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void requiresAuthenticationToReadFavorites() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_REQUIRED"));
    }

    @Test
    void savesFavoriteIdempotently() throws Exception {
        when(favoriteContentService.save(1L, "126508"))
                .thenReturn(new FavoriteContentResponse("126508", true));

        mockMvc.perform(put("/api/v1/members/me/favorites/126508")
                        .with(csrf())
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("favorite_saved"))
                .andExpect(jsonPath("$.data.content_id").value("126508"))
                .andExpect(jsonPath("$.data.is_favorite").value(true));
    }

    @Test
    void deletesFavoriteIdempotently() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/favorites/126508")
                        .with(csrf())
                        .with(authentication(authenticatedMember())))
                .andExpect(status().isNoContent());

        verify(favoriteContentService).delete(1L, "126508");
    }

    private UsernamePasswordAuthenticationToken authenticatedMember() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(1L, MemberStatus.ACTIVE, "000000000000000000000000000000000000000000000000000000000000000a"),
                null,
                List.of());
    }
}
