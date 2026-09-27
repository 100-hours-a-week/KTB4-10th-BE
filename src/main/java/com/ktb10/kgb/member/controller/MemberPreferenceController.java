package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.dto.request.PreferenceUpdateRequest;
import com.ktb10.kgb.member.dto.response.MemberPreferenceResponse;
import com.ktb10.kgb.member.dto.response.PreferenceUpdateResponse;
import com.ktb10.kgb.member.service.MemberPreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 회원의 현재 취향 조회와 전체 교체 API를 제공합니다. */
@RestController
@RequestMapping("/api/v1/members/me/preferences")
public class MemberPreferenceController {

    private static final String GET_SUCCESS_MESSAGE = "preference_get_success";
    private static final String UPDATE_SUCCESS_MESSAGE = "preference_update_success";

    private final MemberPreferenceService memberPreferenceService;

    public MemberPreferenceController(MemberPreferenceService memberPreferenceService) {
        this.memberPreferenceService = memberPreferenceService;
    }

    @GetMapping
    public ApiResponse<MemberPreferenceResponse> getPreferences(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(
                GET_SUCCESS_MESSAGE,
                memberPreferenceService.getPreferences(member.memberId()));
    }

    @PutMapping
    public ApiResponse<PreferenceUpdateResponse> replacePreferences(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody PreferenceUpdateRequest request) {
        return ApiResponse.success(
                UPDATE_SUCCESS_MESSAGE,
                memberPreferenceService.replacePreferences(member.memberId(), request));
    }
}
