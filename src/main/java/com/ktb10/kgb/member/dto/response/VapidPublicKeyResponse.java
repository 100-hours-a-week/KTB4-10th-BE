package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 브라우저가 Push 구독을 만들 때 사용하는 VAPID 공개키 응답입니다. */
public record VapidPublicKeyResponse(
        @JsonProperty("public_key")
        String publicKey) {
}
