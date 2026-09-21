package com.ktb10.kgb.guidebook.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.guidebook.dto.response.GuidebookDetailResponse;
import com.ktb10.kgb.guidebook.dto.response.GuidebookListResponse;
import com.ktb10.kgb.guidebook.service.GuidebookService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회원이 보관 중인 가이드북 조회 API를 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/guidebooks")
public class GuidebookController {

    private static final String DETAIL_SUCCESS_MESSAGE = "guidebook_get_success";
    private static final String LIST_SUCCESS_MESSAGE = "guidebook_list_success";

    private final GuidebookService guidebookService;

    public GuidebookController(GuidebookService guidebookService) {
        this.guidebookService = guidebookService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GuidebookListResponse>> getGuidebooks(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // TODO: 회원 도메인의 보호 API 공통 정책이 제공되면 ACTIVE 상태 검증을 그 경계로 이동한다.
        GuidebookListResponse response = guidebookService.getGuidebooks(
                member.memberId(), cursor, size);
        return ResponseEntity.ok(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @GetMapping("/{guidebookId}")
    public ResponseEntity<ApiResponse<GuidebookDetailResponse>> getGuidebookDetail(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long guidebookId) {
        // TODO: 회원 도메인의 보호 API 공통 정책이 제공되면 ACTIVE 상태 검증을 그 경계로 이동한다.
        GuidebookDetailResponse response = guidebookService.getGuidebookDetail(
                member.memberId(), guidebookId);
        return ResponseEntity.ok(ApiResponse.success(DETAIL_SUCCESS_MESSAGE, response));
    }
}
