package com.ktb10.kgb.guidebook.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.guidebook.dto.response.GuidebookDetailResponse;
import com.ktb10.kgb.guidebook.service.GuidebookQueryService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원이 보관 중인 가이드북 조회 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/guidebooks")
public class GuidebookController {

    private static final String DETAIL_SUCCESS_MESSAGE = "guidebook_get_success";

    private final GuidebookQueryService guidebookQueryService;

    public GuidebookController(GuidebookQueryService guidebookQueryService) {
        this.guidebookQueryService = guidebookQueryService;
    }

    @GetMapping("/{guidebookId}")
    public ResponseEntity<ApiResponse<GuidebookDetailResponse>> getDetail(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long guidebookId) {
        // TODO: 회원 도메인의 보호 API 공통 정책이 제공되면 ACTIVE 상태 검증을 그 경계로 이동한다.
        GuidebookDetailResponse response = guidebookQueryService.getDetail(
                member.memberId(), guidebookId);
        return ResponseEntity.ok(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }
}
