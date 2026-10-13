package com.ktb10.kgb.member.controller;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.member.dto.request.WebPushSubscriptionRequest;
import com.ktb10.kgb.member.dto.response.VapidPublicKeyResponse;
import com.ktb10.kgb.member.dto.response.WebPushSubscriptionResponse;
import com.ktb10.kgb.member.service.VapidPublicKeyService;
import com.ktb10.kgb.member.service.WebPushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** VAPID 공개키와 인증 회원의 브라우저별 Web Push 구독 API를 제공합니다. */
@RestController
@RequestMapping("/api/v1")
public class WebPushSubscriptionController {

    private static final String PUBLIC_KEY_GET_SUCCESS_MESSAGE = "push_vapid_public_key_get_success";
    private static final String SUBSCRIPTION_UPSERT_SUCCESS_MESSAGE =
            "web_push_subscription_upsert_success";

    private final VapidPublicKeyService vapidPublicKeyService;
    private final WebPushSubscriptionService subscriptionService;

    public WebPushSubscriptionController(
            VapidPublicKeyService vapidPublicKeyService,
            WebPushSubscriptionService subscriptionService) {
        this.vapidPublicKeyService = vapidPublicKeyService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/push/vapid-public-key")
    public ApiResponse<VapidPublicKeyResponse> getVapidPublicKey() {
        return ApiResponse.success(
                PUBLIC_KEY_GET_SUCCESS_MESSAGE,
                vapidPublicKeyService.getPublicKey());
    }

    @PutMapping("/members/me/push-subscriptions")
    public ApiResponse<WebPushSubscriptionResponse> registerOrRenew(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody WebPushSubscriptionRequest request) {
        return ApiResponse.success(
                SUBSCRIPTION_UPSERT_SUCCESS_MESSAGE,
                subscriptionService.registerOrRenew(
                        member.memberId(),
                        member.sessionId(),
                        request));
    }

    @DeleteMapping("/members/me/push-subscriptions/{subscriptionId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long subscriptionId) {
        subscriptionService.revoke(member.memberId(), subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
