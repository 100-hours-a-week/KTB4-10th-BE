package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.dto.request.UpdateMemberSettingsRequest;
import com.ktb10.kgb.member.dto.response.MemberResponse;
import com.ktb10.kgb.member.dto.response.MemberSettingsResponse;
import com.ktb10.kgb.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 회원의 기본 정보 API를 제공합니다. */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private static final String GET_ME_SUCCESS_MESSAGE = "member_get_success";
    private static final String GET_SETTINGS_SUCCESS_MESSAGE = "member_setting_get_success";
    private static final String UPDATE_SETTINGS_SUCCESS_MESSAGE = "member_setting_update_success";

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMe(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(
                GET_ME_SUCCESS_MESSAGE,
                memberService.getMe(member.memberId()));
    }

    @GetMapping("/me/settings")
    public ApiResponse<MemberSettingsResponse> getSettings(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(
                GET_SETTINGS_SUCCESS_MESSAGE,
                memberService.getSettings(member.memberId()));
    }

    @PatchMapping("/me/settings")
    public ApiResponse<MemberSettingsResponse> updateSettings(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody UpdateMemberSettingsRequest request) {
        return ApiResponse.success(
                UPDATE_SETTINGS_SUCCESS_MESSAGE,
                memberService.updatePushEnabled(member.memberId(), request.pushEnabled()));
    }
}
