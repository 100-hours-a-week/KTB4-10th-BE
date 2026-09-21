package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.member.dto.response.PreferenceOptionResponse;
import com.ktb10.kgb.member.service.PreferenceOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 온보딩과 취향 수정 화면에 취향 선택지를 제공합니다. */
@RestController
@RequestMapping("/api/v1/preference-options")
public class PreferenceController {

    private static final String GET_OPTIONS_SUCCESS_MESSAGE =
            "preference_option_list_success";

    private final PreferenceOptionService preferenceOptionService;

    public PreferenceController(PreferenceOptionService preferenceOptionService) {
        this.preferenceOptionService = preferenceOptionService;
    }

    @GetMapping
    public ApiResponse<PreferenceOptionResponse> getOptions() {
        return ApiResponse.success(
                GET_OPTIONS_SUCCESS_MESSAGE,
                preferenceOptionService.getOptions());
    }
}
