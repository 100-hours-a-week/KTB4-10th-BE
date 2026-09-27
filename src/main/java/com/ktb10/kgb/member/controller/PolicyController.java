package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.member.dto.response.PolicyListResponse;
import com.ktb10.kgb.member.dto.response.PolicyResponse;
import com.ktb10.kgb.member.service.PolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 전에도 확인할 수 있는 정책 안내 API입니다. */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private static final String LIST_SUCCESS_MESSAGE = "policy_list_success";
    private static final String GET_SUCCESS_MESSAGE = "policy_get_success";

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ApiResponse<PolicyListResponse> getPolicies() {
        return ApiResponse.success(LIST_SUCCESS_MESSAGE, policyService.getPolicies());
    }

    @GetMapping("/{policyType}")
    public ApiResponse<PolicyResponse> getPolicy(@PathVariable String policyType) {
        return ApiResponse.success(GET_SUCCESS_MESSAGE, policyService.getPolicy(policyType));
    }
}
