package com.ktb10.kgb.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 브라우저 PushSubscription이 제공하는 payload 암호화 키입니다. */
public record WebPushSubscriptionKeysRequest(
        @NotBlank
        @Size(max = 255)
        String p256dh,
        @NotBlank
        @Size(max = 255)
        String auth) {
}
