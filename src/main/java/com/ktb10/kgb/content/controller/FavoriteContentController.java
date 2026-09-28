package com.ktb10.kgb.content.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.content.dto.FavoriteContentResponse;
import com.ktb10.kgb.content.service.FavoriteContentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 회원의 관심 장소 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/members/me/favorites")
public class FavoriteContentController {

    private static final String SAVED_MESSAGE = "favorite_saved";

    private final FavoriteContentService favoriteContentService;

    public FavoriteContentController(FavoriteContentService favoriteContentService) {
        this.favoriteContentService = favoriteContentService;
    }

    @PutMapping("/{contentId}")
    public ResponseEntity<ApiResponse<FavoriteContentResponse>> save(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @NotBlank @Size(max = 100) String contentId) {
        FavoriteContentResponse response = favoriteContentService.save(
                member.memberId(), contentId);
        return ResponseEntity.ok(ApiResponse.success(SAVED_MESSAGE, response));
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @NotBlank @Size(max = 100) String contentId) {
        favoriteContentService.delete(member.memberId(), contentId);
        return ResponseEntity.noContent().build();
    }
}
